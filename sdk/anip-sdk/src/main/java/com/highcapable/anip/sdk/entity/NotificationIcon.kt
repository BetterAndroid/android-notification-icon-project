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
package com.highcapable.anip.sdk.entity

import android.graphics.Bitmap
import androidx.annotation.ColorInt
import com.highcapable.anip.sdk.Anip
import com.highcapable.anip.sdk.internal.bundle.BundlePath
import com.highcapable.anip.sdk.internal.bundle.InMemoryBundle
import com.highcapable.anip.sdk.internal.cache.BitmapCache
import com.highcapable.anip.sdk.internal.icon.IconFormat
import com.highcapable.anip.sdk.internal.icon.LocalizedLabel
import com.highcapable.anip.sdk.internal.icon.RuleSource
import com.highcapable.anip.sdk.internal.icon.rule.IconResolvedRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ANIP notification icon entity.
 * @param packageName the package name owned by this rule.
 * @param color the notification icon color, or null when the caller's default should be used.
 * @param overlay whether every notification icon from the application should be replaced.
 * @param contributors resolved contributor list.
 */
class NotificationIcon private constructor(
    val packageName: String,
    @ColorInt val color: Int?,
    val overlay: Boolean,
    val contributors: List<String>,
    private val localizedLabel: LocalizedLabel,
    private val iconFormat: IconFormat,
    private val iconPath: BundlePath,
    private val bundle: InMemoryBundle,
    private val bitmapCache: BitmapCache,
    internal val ruleSource: RuleSource
) {

    internal companion object {

        /**
         * Creates an icon backed by a resolved rule and its lazy bitmap dependencies.
         * @param packageName the package name exposed by the resulting icon.
         * @param resolvedRule the resolved metadata and inherited resource properties.
         * @param iconPath the bundle path of the resource decoded on first access.
         * @param bundle the bundle that holds the resource content.
         * @param bitmapCache the cache owned by the creating [Anip] instance.
         * @param ruleSource the category source used by catalog filtering.
         * @return [NotificationIcon]
         */
        fun create(
            packageName: String,
            resolvedRule: IconResolvedRule,
            iconPath: BundlePath,
            bundle: InMemoryBundle,
            bitmapCache: BitmapCache,
            ruleSource: RuleSource
        ) = NotificationIcon(
            packageName = packageName,
            color = resolvedRule.color,
            overlay = resolvedRule.overlay,
            contributors = resolvedRule.contributors,
            localizedLabel = resolvedRule.label,
            iconFormat = resolvedRule.format,
            iconPath = iconPath,
            bundle = bundle,
            bitmapCache = bitmapCache,
            ruleSource = ruleSource
        )
    }

    /** Resource path identity used to preserve bitmap sharing while building a snapshot. */
    internal val bitmapResourcePath get() = iconPath

    /** App label resolved for the current locale. */
    val label get() = localizedLabel.resolve()

    /** All localized labels declared by this icon rule. */
    val availableLabels get() = localizedLabel.translations

    /**
     * Decodes this entity's icon resource and caches the bitmap in the owning [Anip] instance.
     *
     * Decoding runs on [Dispatchers.IO].
     * A missing icon resource or decoding failure returns null.
     * @return [Bitmap] or null when it cannot be loaded.
     */
    suspend fun loadBitmap() = withContext(Dispatchers.IO) {
        bundle.read(iconPath)?.let { bitmapCache.load(iconPath, it, iconFormat) }
    }

    override fun toString() = "NotificationIcon(" +
        "packageName='$packageName', " +
        "label=$label, " +
        "availableLabels=$availableLabels, " +
        "color=$color, " +
        "iconFormat=$iconFormat, " +
        "iconPath=$iconPath, " +
        "ruleSource=$ruleSource, " +
        "overlay=$overlay, " +
        "contributors=$contributors" +
        ")"
}