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
 * This file is created by fankes on 2026/9/4.
 */
@file:Suppress("FunctionName")

package com.highcapable.anip.sdk.config

import com.highcapable.anip.sdk.internal.utils.sha256
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Remote source of an ANIP resource manifest.
 *
 * Instances are created through the templates in [Companion].
 * @param manifestUrl the canonical URL of `anip-release.json`.
 * @param urlResolver optionally resolves each final manifest or bundle request URL.
 */
@ConsistentCopyVisibility
data class RemoteSource internal constructor(
    val manifestUrl: String,
    val urlResolver: UrlResolver? = null
) {

    companion object {

        /** Official GitHub repository that publishes ANIP resources. */
        const val GITHUB_OFFICIAL_REPO_SLUG = "BetterAndroid/android-notification-icon-project"

        /** File name of the release manifest published with each resource release. */
        internal const val RELEASE_MANIFEST_FILE_NAME = "anip-release.json"

        private const val GITHUB_HOST = "https://github.com"
        private const val GITLAB_HOST = "https://gitlab.com"

        private val GITHUB_REPOSITORY_PATTERN = "^[A-Za-z0-9][A-Za-z0-9-]{0,38}/[A-Za-z0-9_.-]+$".toRegex()
        private val GITLAB_PROJECT_PATTERN = "^[A-Za-z0-9][A-Za-z0-9_.-]*(/[A-Za-z0-9][A-Za-z0-9_.-]*)+$".toRegex()

        /**
         * Creates a GitHub Releases source.
         * @param repository repository in `owner/repository` form.
         * @param urlResolver optional request URL resolver.
         * @return [RemoteSource]
         */
        @JvmStatic
        @JvmOverloads
        fun GitHub(
            repository: String = GITHUB_OFFICIAL_REPO_SLUG,
            urlResolver: UrlResolver? = null
        ): RemoteSource {
            val normalizedRepository = repository.trim()
            val repositoryName = normalizedRepository.substringAfter('/', "")
            require(
                GITHUB_REPOSITORY_PATTERN.matches(normalizedRepository) && repositoryName != "." && repositoryName != ".."
            ) { "GitHub repository must use owner/repository form." }

            return RemoteSource(
                manifestUrl = "$GITHUB_HOST/$normalizedRepository/releases/latest/download/$RELEASE_MANIFEST_FILE_NAME",
                urlResolver = urlResolver
            )
        }

        /**
         * Creates a GitLab latest-release source using a direct asset path.
         * @param project project in `namespace/project` form, nested groups are supported.
         * @param host GitLab instance URL.
         * @param urlResolver optional request URL resolver.
         * @return [RemoteSource]
         */
        @JvmStatic
        @JvmOverloads
        fun GitLab(
            project: String,
            host: String = GITLAB_HOST,
            urlResolver: UrlResolver? = null
        ): RemoteSource {
            val normalizedProject = project.trim().trim('/')
            require(GITLAB_PROJECT_PATTERN.matches(normalizedProject)) {
                "GitLab project must use namespace/project form."
            }

            val urlBuilder = host.requireBaseUrl("GitLab host").newBuilder()
            normalizedProject.split('/').forEach(urlBuilder::addPathSegment)
            listOf("-", "releases", "permalink", "latest", "downloads", RELEASE_MANIFEST_FILE_NAME)
                .forEach(urlBuilder::addPathSegment)
            return RemoteSource(urlBuilder.build().toString(), urlResolver)
        }

        /**
         * Creates a source whose manifest is stored under a static base URL.
         * @param baseUrl directory URL containing `anip-release.json`.
         * @param urlResolver optional request URL resolver.
         * @return [RemoteSource]
         */
        @JvmStatic
        @JvmOverloads
        fun Static(
            baseUrl: String,
            urlResolver: UrlResolver? = null
        ) = RemoteSource(
            manifestUrl = baseUrl.requireBaseUrl("Static source base URL")
                .newBuilder()
                .addPathSegment(RELEASE_MANIFEST_FILE_NAME)
                .build()
                .toString(),
            urlResolver = urlResolver
        )

        /**
         * Creates a source from an exact resource manifest URL.
         * @param manifestUrl complete URL of `anip-release.json`.
         * @param urlResolver optional request URL resolver.
         * @return [RemoteSource]
         */
        @JvmStatic
        @JvmOverloads
        fun Custom(
            manifestUrl: String,
            urlResolver: UrlResolver? = null
        ) = RemoteSource(
            manifestUrl = manifestUrl.requireHttpUrl("Resource manifest URL").toString(),
            urlResolver = urlResolver
        )

        /** Parses and validates an HTTP base URL without query parameters or fragments. */
        private fun String.requireBaseUrl(name: String) = requireHttpUrl(name).also {
            require(it.query == null && it.fragment == null) { "$name must not contain a query or fragment." }
        }

        private fun String.requireHttpUrl(name: String): HttpUrl {
            val normalizedValue = trim()
            require(normalizedValue.isNotEmpty()) { "$name must not be empty." }

            return runCatching {
                normalizedValue.toHttpUrl()
            }.getOrElse {
                if (it is IllegalArgumentException) throw IllegalArgumentException("$name is invalid.", it)
                throw it
            }
        }
    }

    /**
     * Resolves the final URL for an ANIP remote resource.
     */
    fun interface UrlResolver {

        /**
         * Resolves the final request URL used for a release manifest or resource bundle.
         *
         * This callback is invoked synchronously and must only calculate a URL without performing network I/O.
         * @param sourceUrl canonical source URL.
         * @param tag release tag for a resource bundle, or null for the latest-release manifest.
         * @param assetName manifest or resource bundle file name.
         * @return [String]
         */
        fun resolve(sourceUrl: String, tag: String?, assetName: String): String
    }

    /** Validates copied instances before they are used. */
    internal fun validate() {
        runCatching {
            manifestUrl.toHttpUrl()
        }.getOrElse {
            if (it is IllegalArgumentException) throw IllegalArgumentException("Resource manifest URL is invalid.", it)
            throw it
        }
    }

    /**
     * Creates a stable cache identity from the canonical manifest URL before URL resolution.
     * @return [String]
     */
    internal fun createIdentity() = manifestUrl.toByteArray().sha256()
}