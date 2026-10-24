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
package com.highcapable.anip.sdk.internal.source

import com.highcapable.anip.sdk.config.RemoteSource
import com.highcapable.anip.sdk.internal.bundle.BundleRelease
import com.highcapable.anip.sdk.internal.exception.BundleException
import com.highcapable.anip.sdk.internal.source.dto.ReleaseManifest
import com.highcapable.anip.sdk.internal.utils.asHexString
import com.highcapable.anip.sdk.internal.utils.readLimited
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.security.DigestInputStream
import java.security.MessageDigest
import kotlin.coroutines.resumeWithException

/**
 * Fetches a static release manifest and downloads its validated ANIP resource bundle.
 * @param source the configured resource source.
 * @param maxBundleSizeBytes the maximum accepted resource bundle size.
 */
internal class ResourceReleaseClient(private val source: RemoteSource, private val maxBundleSizeBytes: Long) {

    private companion object {

        const val USER_AGENT = "ANIP-SDK"
        const val RELEASE_MANIFEST_FILE_NAME = "anip-release.json"

        const val RELEASE_MANIFEST_SCHEMA_VERSION = 1
        const val MAX_RELEASE_MANIFEST_SIZE_BYTES = 64L * 1024

        const val MANIFEST_MEDIA_TYPE = "application/json"
        const val DOWNLOAD_MEDIA_TYPE = "application/octet-stream"

        val RELEASE_TAG_PATTERN = "^[0-9a-fA-F]{7}$".toRegex()
        val SHA256_PATTERN = "^[0-9a-fA-F]{64}$".toRegex()
    }

    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val releaseManifestSourceUrl = source.manifestUrl.toHttpUrl()

    /**
     * Fetches and validates the metadata published as `anip-release.json` on the latest release.
     * @return [BundleRelease]
     */
    suspend fun fetchLatest() = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(releaseManifestSourceUrl.toString().resolveUrl(tag = null, assetName = RELEASE_MANIFEST_FILE_NAME))
                .header("Accept", MANIFEST_MEDIA_TYPE)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            httpClient.newCall(request).useResponse { response ->
                if (!response.isSuccessful) throw BundleException("Release manifest request failed: HTTP ${response.code}")

                val body = response.body
                val contentLength = body.contentLength()
                if (contentLength > MAX_RELEASE_MANIFEST_SIZE_BYTES) throw BundleException("Release manifest exceeds the size limit.")

                val content = body.byteStream().readLimited(MAX_RELEASE_MANIFEST_SIZE_BYTES).toString(Charsets.UTF_8)
                val releaseManifest = runCatching {
                    json.decodeFromString<ReleaseManifest>(content)
                }.getOrElse {
                    throw BundleException("Invalid release manifest.", it)
                }
                parseBundleRelease(releaseManifest)
            }
        }.getOrElse {
            when (it) {
                is CancellationException, is BundleException -> throw it
                else -> {
                    currentCoroutineContext().ensureActive()
                    throw BundleException("Failed to fetch the latest release manifest.", it)
                }
            }
        }
    }

    /**
     * Downloads [bundleRelease] into memory while enforcing declared size, maximum size, and SHA-256.
     * @return [ByteArray] the verified bundle archive.
     */
    suspend fun download(bundleRelease: BundleRelease): ByteArray = withContext(Dispatchers.IO) {
        runCatching {
            val expectedSize = bundleRelease.assetSize
            if (expectedSize !in 1..maxBundleSizeBytes)
                throw BundleException("Release asset exceeds the size limit: $expectedSize bytes.")

            val downloadUrl = bundleRelease.downloadUrl.resolveUrl(
                tag = bundleRelease.tag,
                assetName = bundleRelease.assetName
            )
            val request = Request.Builder()
                .url(downloadUrl)
                .header("Accept", DOWNLOAD_MEDIA_TYPE)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            httpClient.newCall(request).useResponse { response ->
                if (!response.isSuccessful) throw BundleException("Release download failed: HTTP ${response.code}")

                val body = response.body
                val contentLength = body.contentLength()
                if (contentLength > maxBundleSizeBytes) throw BundleException("Release response exceeds the size limit: $contentLength bytes.")

                val digest = MessageDigest.getInstance("SHA-256")
                val archive = DigestInputStream(body.byteStream(), digest).buffered().use { inputStream ->
                    inputStream.readLimited(maxBundleSizeBytes)
                }

                val downloadedBytes = archive.size.toLong()
                if (downloadedBytes != expectedSize) throw BundleException("Release size verification failed: $downloadedBytes/$expectedSize")

                val actual = digest.digest().asHexString()
                if (!actual.equals(bundleRelease.sha256, ignoreCase = true)) throw BundleException("Release SHA-256 verification failed.")

                archive
            }
        }.getOrElse {
            currentCoroutineContext().ensureActive()

            if (it is CancellationException || it is BundleException) throw it
            throw BundleException("Failed to download the release.", it)
        }
    }

    private fun parseBundleRelease(releaseManifest: ReleaseManifest): BundleRelease {
        if (releaseManifest.schemaVersion != RELEASE_MANIFEST_SCHEMA_VERSION)
            throw BundleException("Unsupported release manifest schema: ${releaseManifest.schemaVersion}")

        val tag = releaseManifest.tag.required("tag")
        if (!RELEASE_TAG_PATTERN.matches(tag)) throw BundleException("Invalid release tag: $tag")

        val expectedAssetName = "anip-bundle-$tag.zip"
        val assetName = releaseManifest.assetName.required("assetName")
        if (assetName != expectedAssetName)
            throw BundleException("Invalid release asset name: $assetName")

        val timestamp = releaseManifest.timestamp
        if (timestamp <= 0) throw BundleException("Invalid release timestamp: $timestamp")

        val assetSize = releaseManifest.size
        if (assetSize !in 1..maxBundleSizeBytes)
            throw BundleException("Release asset exceeds the size limit: $assetSize bytes.")

        val sha256 = releaseManifest.sha256.required("sha256")
        if (!SHA256_PATTERN.matches(sha256)) throw BundleException("Invalid release SHA-256: $sha256")

        val downloadUrl = releaseManifest.downloadUrl.required("downloadUrl")
        val parsedDownloadUrl = releaseManifestSourceUrl.resolve(downloadUrl)
            ?: throw BundleException("Invalid release download URL.")

        return BundleRelease(tag, timestamp, expectedAssetName, parsedDownloadUrl.toString(), assetSize, sha256)
    }

    private fun String?.required(fieldName: String) = this?.trim()?.takeIf(String::isNotEmpty)
        ?: throw BundleException("Release manifest field is missing: $fieldName")

    /** Resolves and validates a request URL with its release context without falling back to the source URL. */
    private fun String.resolveUrl(tag: String?, assetName: String): HttpUrl {
        val sourceUrl = this
        val resolvedUrl = source.urlResolver?.let { resolver ->
            runCatching {
                resolver.resolve(sourceUrl, tag, assetName)
            }.getOrElse {
                when (it) {
                    is CancellationException -> throw it
                    else -> throw BundleException("The URL resolver failed.", it)
                }
            }
        }?.trim() ?: sourceUrl
        if (resolvedUrl.isEmpty()) throw BundleException("The URL resolver returned an empty URL.")

        return runCatching {
            resolvedUrl.toHttpUrl()
        }.getOrElse {
            if (it is IllegalArgumentException) throw BundleException("The URL resolver returned an invalid URL.", it)
            throw it
        }
    }

    /** Awaits this OkHttp call and cancels it when the surrounding coroutine is canceled. */
    private suspend fun Call.await() = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }

        enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive)
                    continuation.resume(response) { _, value, _ -> value.close() }
                else response.close()
            }
        })
    }

    /** Executes [block] with an awaited response and always closes both response and cancellation handle. */
    private suspend fun <T> Call.useResponse(block: (Response) -> T): T {
        val cancellationHandle = currentCoroutineContext().job.invokeOnCompletion { cause ->
            if (cause is CancellationException) cancel()
        }

        return try {
            await().use(block)
        } finally {
            cancellationHandle.dispose()
        }
    }
}