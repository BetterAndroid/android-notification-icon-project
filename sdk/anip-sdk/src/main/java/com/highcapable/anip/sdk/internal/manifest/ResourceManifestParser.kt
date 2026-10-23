/*
 * ANIP - Provides standardized monochrome icon resources for apps and vendor systems that do not conform to the Android standard notification design.
 * Copyright (C) 2019 HighCapable
 * https://github.com/BetterAndroid/android-notification-icon-project
 *
 * Apache License Version 2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This file is created by fankes on 2026/8/28.
 */
package com.highcapable.anip.sdk.internal.manifest

import androidx.core.graphics.toColorInt
import com.highcapable.anip.sdk.entity.NotificationIcon
import com.highcapable.anip.sdk.internal.bundle.BundlePath
import com.highcapable.anip.sdk.internal.bundle.InMemoryBundle
import com.highcapable.anip.sdk.internal.cache.BitmapCache
import com.highcapable.anip.sdk.internal.exception.BundleException
import com.highcapable.anip.sdk.internal.icon.IconCatalog
import com.highcapable.anip.sdk.internal.icon.IconFormat
import com.highcapable.anip.sdk.internal.icon.LocalizedLabel
import com.highcapable.anip.sdk.internal.icon.RuleSource
import com.highcapable.anip.sdk.internal.icon.rule.IconRawRule
import com.highcapable.anip.sdk.internal.icon.rule.IconResolvedRule
import com.highcapable.anip.sdk.internal.manifest.dto.ManifestRule
import com.highcapable.anip.sdk.type.SystemVariant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Locale

/**
 * Parses the manifests and icon files of an ANIP bundle into an in-memory catalog.
 * @param bitmapCache instance-scoped cache shared by the icons created from this parser.
 */
internal class ResourceManifestParser(private val bitmapCache: BitmapCache) {

    private companion object {

        const val MANIFEST_FILE_NAME = "manifest.json"
        const val ICON_RESOURCES_DIR_NAME = "res"

        val COLOR_PATTERN = "^#[0-9A-F]{6}$".toRegex()
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Parses application, game, and selected system resources from [bundle].
     * @param bundle the bundle content to parse.
     * @param systemVariant system resources to include, or null to exclude all system resources.
     * @return [IconCatalog]
     */
    fun parse(bundle: InMemoryBundle, systemVariant: SystemVariant?): IconCatalog {
        val ruleSources = buildList {
            add(RuleSource.APP)
            add(RuleSource.GAME)
            systemVariant?.ruleSources?.let(::addAll)
        }
        return IconCatalog(ruleSources.flatMap { parseRuleSource(bundle, it) })
    }

    /**
     * Parses one manifest category and resolves each rule against the resources stored beside it.
     * @throws BundleException when the category is missing, malformed, or references an invalid resource.
     */
    private fun parseRuleSource(bundle: InMemoryBundle, ruleSource: RuleSource) = runCatching {
        val manifestContent = bundle.read(ruleSource.relativePath.resolve(MANIFEST_FILE_NAME))
            ?: throw BundleException("$MANIFEST_FILE_NAME does not exist in ${ruleSource.relativePath}")
        val resourceDirectory = ruleSource.relativePath.resolve(ICON_RESOURCES_DIR_NAME)
        if (!bundle.containsDirectory(resourceDirectory))
            throw BundleException("Icon resources directory does not exist in ${ruleSource.relativePath}")

        val manifestRules = json.decodeFromString<Map<String, ManifestRule>>(manifestContent.toString(Charsets.UTF_8))
        val rawRules = manifestRules.mapValues { (packageName, manifestRule) ->
            if (!BundlePath.isValidSegment(packageName)) throw BundleException("Unsafe ANIP rule package name: $packageName")

            parseRule(packageName, manifestRule)
        }

        val resolvedRules = mutableMapOf<String, IconResolvedRule>()
        val resolvingPackages = mutableSetOf<String>()

        rawRules.map { (packageName, _) ->
            val resolvedRule = resolveRule(
                packageName = packageName,
                rawRules = rawRules,
                resolvedRules = resolvedRules,
                resolvingPackages = resolvingPackages
            )

            createNotificationIcon(
                packageName = packageName,
                resolvedRule = resolvedRule,
                bundle = bundle,
                resourceDirectory = resourceDirectory,
                ruleSource = ruleSource
            )
        }
    }.getOrElse {
        if (it is BundleException) throw it
        throw BundleException("Failed to parse ANIP rules: ${ruleSource.relativePath}", it)
    }

    /**
     * Resolves a raw rule, recursively inheriting fields from its target while rejecting target cycles.
     * Resolved rules are memoized in [resolvedRules] so each package is resolved at most once.
     */
    private fun resolveRule(
        packageName: String,
        rawRules: Map<String, IconRawRule>,
        resolvedRules: MutableMap<String, IconResolvedRule>,
        resolvingPackages: MutableSet<String>
    ): IconResolvedRule {
        resolvedRules[packageName]?.let { return it }

        val rawRule = rawRules[packageName] ?: throw BundleException("ANIP \"target\" does not exist: $packageName")
        if (!resolvingPackages.add(packageName)) throw BundleException("Circular ANIP \"target\" inheritance: $packageName")

        return runCatching {
            val resolvedRule = rawRule.target?.let { target ->
                val parentRule = resolveRule(target, rawRules, resolvedRules, resolvingPackages)

                IconResolvedRule(
                    label = rawRule.label ?: parentRule.label,
                    format = parentRule.format,
                    color = rawRule.color ?: parentRule.color,
                    overlay = rawRule.overlay ?: parentRule.overlay,
                    contributors = rawRule.contributors ?: parentRule.contributors,
                    resourcePackageName = parentRule.resourcePackageName
                )
            } ?: IconResolvedRule(
                label = rawRule.label ?: throw BundleException("ANIP rule is missing label: $packageName"),
                format = rawRule.format ?: throw BundleException("ANIP rule is missing format: $packageName"),
                color = rawRule.color,
                overlay = rawRule.overlay ?: false,
                contributors = rawRule.contributors ?: throw BundleException("ANIP rule is missing contributors: $packageName"),
                resourcePackageName = packageName
            )

            resolvedRules[packageName] = resolvedRule
            resolvedRule
        }.also {
            resolvingPackages.remove(packageName)
        }.getOrThrow()
    }

    /** Verifies the resolved resource path and creates the lazily decoded icon exposed by the catalog. */
    private fun createNotificationIcon(
        packageName: String,
        resolvedRule: IconResolvedRule,
        bundle: InMemoryBundle,
        resourceDirectory: BundlePath,
        ruleSource: RuleSource
    ): NotificationIcon {
        val iconPath = resourceDirectory.resolve("${resolvedRule.resourcePackageName}.${resolvedRule.format.extension}")
        if (!bundle.contains(iconPath)) throw BundleException("Icon file does not exist: $iconPath")

        return NotificationIcon.create(
            packageName = packageName,
            resolvedRule = resolvedRule,
            ruleSource = ruleSource,
            iconPath = iconPath,
            bundle = bundle,
            bitmapCache = bitmapCache
        )
    }

    private fun parseRule(packageName: String, manifestRule: ManifestRule): IconRawRule {
        val target = manifestRule.target.toOptionalString("target", packageName)

        return IconRawRule(
            label = manifestRule.label?.let { parseLabel(packageName, it) },
            target = target,
            format = if (target == null)
                manifestRule.format.toOptionalString("format", packageName)?.toIconFormat(packageName)
            else null,
            color = manifestRule.color.toOptionalString("color", packageName)?.toIconColor(packageName),
            overlay = manifestRule.overlay,
            contributors = manifestRule.contributors.toOptionalString("contributors", packageName)
                ?.toContributors(packageName)
        )
    }

    /** Parses either a plain label or a locale-to-label object and validates every localized entry. */
    private fun parseLabel(packageName: String, value: JsonElement): LocalizedLabel = when (value) {
        is JsonPrimitive -> {
            if (!value.isString) throw BundleException("Invalid ANIP label: $packageName")

            val label = value.content.trim().takeIf(String::isNotEmpty)
                ?: throw BundleException("ANIP label must not be blank: $packageName")

            LocalizedLabel(label, emptyMap())
        }
        is JsonObject -> {
            if (value.isEmpty()) throw BundleException("ANIP localized label must not be empty: $packageName")

            val translations = linkedMapOf<Locale, String>()
            value.forEach { (languageTag, labelValue) ->
                val label = labelValue as? JsonPrimitive
                if (label?.isString != true) throw BundleException("Invalid ANIP localized label: $packageName/$languageTag")

                val locale = Locale.forLanguageTag(languageTag)
                if (locale.language.isBlank() || locale.toLanguageTag() == "und")
                    throw BundleException("Invalid ANIP language tag: $packageName/$languageTag")

                val localizedLabel = label.content.trim().takeIf(String::isNotEmpty)
                    ?: throw BundleException("Invalid ANIP localized label: $packageName/$languageTag")
                if (translations.put(locale, localizedLabel) != null)
                    throw BundleException("Duplicate ANIP language tag: $packageName/$languageTag")
            }

            LocalizedLabel(null, translations)
        }
        else -> throw BundleException("Invalid ANIP label: $packageName")
    }

    /** Trims an optional manifest string and rejects values that become empty. */
    private fun String?.toOptionalString(name: String, packageName: String) = this?.let { value ->
        value.trim().takeIf(String::isNotEmpty) ?: throw BundleException("Invalid ANIP $name: $packageName")
    }

    private fun String.toIconFormat(packageName: String) = when (lowercase(Locale.ROOT)) {
        IconFormat.PNG.extension -> IconFormat.PNG
        IconFormat.SVG.extension -> IconFormat.SVG
        else -> throw BundleException("Unsupported ANIP format: $packageName/$this")
    }

    /** Validates and converts a manifest `#RRGGBB` color into an Android color integer. */
    private fun String.toIconColor(packageName: String): Int {
        if (!COLOR_PATTERN.matches(this)) throw BundleException("Invalid ANIP color: $packageName/$this")
        return toColorInt()
    }

    /** Splits the comma-separated contributor field while rejecting empty contributor names. */
    private fun String.toContributors(packageName: String) = split(',')
        .map(String::trim)
        .also { contributors ->
            if (contributors.any(String::isEmpty)) throw BundleException("Invalid ANIP contributors: $packageName")
        }
}