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
package com.highcapable.anip.sdk.internal.bundle

import android.os.ParcelFileDescriptor
import com.highcapable.anip.sdk.internal.bundle.dto.BundleHeader
import com.highcapable.anip.sdk.internal.exception.BundleException
import com.highcapable.anip.sdk.internal.icon.IconCatalog
import com.highcapable.anip.sdk.internal.manifest.ResourceManifestParser
import com.highcapable.anip.sdk.internal.utils.readLimited
import com.highcapable.anip.sdk.internal.utils.sha256
import com.highcapable.anip.sdk.type.SystemVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Owns the single ANIP cache file: `MAGIC`, format version, header size, JSON [BundleHeader], then the original
 * bundle archive.
 *
 * Writes replace the content in place and are not atomic. A partly written file fails the SHA-256 check on the
 * next read and is treated as missing.
 * @param sourceIdentity stable identity of the configured resource source.
 * @param resourceManifestParser the parser used to validate cached and downloaded bundles.
 * @param maxBundleSizeBytes the maximum bundle archive size.
 * @param maxExtractedSizeBytes the maximum total uncompressed bundle size.
 * @param maxArchiveEntries the maximum number of ZIP entries accepted from a bundle.
 * @param openForRead opens a readable descriptor of the cache file.
 * @param openForWrite opens a writable descriptor of the cache file, or null when the cache is read-only.
 */
internal class BundleStore(
    private val sourceIdentity: String,
    private val resourceManifestParser: ResourceManifestParser,
    private val maxBundleSizeBytes: Long,
    private val maxExtractedSizeBytes: Long,
    private val maxArchiveEntries: Int,
    private val openForRead: () -> ParcelFileDescriptor,
    private val openForWrite: (() -> ParcelFileDescriptor)?
) {

    private companion object {

        /** `ANIP` in ASCII. */
        const val MAGIC = 0x414E4950

        const val FORMAT_VERSION = 1
        const val MAX_HEADER_SIZE_BYTES = 64 * 1024
    }

    private val json = Json { ignoreUnknownKeys = true }

    private class CachedBundle(
        val header: BundleHeader,
        val archive: ByteArray
    )

    /**
     * Source commit timestamp of the cached release.
     *
     * Only the cache header is read, so a damaged archive still reports its timestamp. The value resolves to `0L`
     * when the cache is absent, invalid, or bound to another source.
     */
    val timestamp
        get() = runCatching {
            ParcelFileDescriptor.AutoCloseInputStream(openForRead()).use { stream ->
                readHeader(DataInputStream(stream.buffered()))
            }.takeIf { it.sourceIdentity == sourceIdentity }?.timestamp
        }.getOrNull() ?: 0L

    /**
     * Returns the writable descriptor opener.
     * @return opener of a writable descriptor.
     * @throws BundleException when the cache is read-only.
     */
    fun requireWritable() = openForWrite ?: throw BundleException("The ANIP cache is read-only.")

    /**
     * Returns a validated catalog for [bundleRelease], downloading and replacing the cache file when required.
     *
     * A downloaded bundle is parsed before it is written, so an invalid release never replaces the cache.
     * @param bundleRelease the validated release metadata.
     * @param systemVariant system resources to include during validation and parsing, or null to exclude them.
     * @param forceRefresh whether the cached bundle must be replaced even if it matches [bundleRelease].
     * @param downloadBundle the callback that returns the verified release archive.
     * @return [IconCatalog]
     */
    suspend fun obtain(
        bundleRelease: BundleRelease,
        systemVariant: SystemVariant?,
        forceRefresh: Boolean,
        downloadBundle: suspend () -> ByteArray
    ) = withContext(Dispatchers.IO) {
        val openForWrite = requireWritable()

        if (!forceRefresh) {
            val cachedCatalog = runCatching {
                readCache()?.takeIf { it.header.sha256.equals(bundleRelease.sha256, ignoreCase = true) }?.let {
                    parse(it.archive, systemVariant)
                }
            }.getOrNull()
            if (cachedCatalog != null) return@withContext cachedCatalog
        }

        val archive = downloadBundle()
        val iconCatalog = parse(archive, systemVariant)
        val header = BundleHeader(
            sourceIdentity = sourceIdentity,
            tag = bundleRelease.tag,
            timestamp = bundleRelease.timestamp,
            size = archive.size.toLong(),
            sha256 = bundleRelease.sha256.lowercase()
        )
        writeCache(openForWrite, header, archive)

        iconCatalog
    }

    /**
     * Loads the cached catalog without performing network access.
     * @param systemVariant system resources to include, or null to exclude them.
     * @return [IconCatalog] or null when the cache belongs to another source.
     * @throws Exception when the cache is missing, damaged, or cannot be parsed.
     */
    fun loadCached(systemVariant: SystemVariant?) = readCache()?.let { parse(it.archive, systemVariant) }

    private fun readCache() = ParcelFileDescriptor.AutoCloseInputStream(openForRead()).use { stream ->
        val input = DataInputStream(stream.buffered())
        val header = readHeader(input)
        if (header.sourceIdentity != sourceIdentity) return@use null

        val archive = input.readLimited(maxBundleSizeBytes)
        if (archive.size.toLong() != header.size || archive.sha256() != header.sha256)
            throw BundleException("ANIP cache verification failed.")

        CachedBundle(header, archive)
    }

    private fun readHeader(input: DataInputStream): BundleHeader {
        if (input.readInt() != MAGIC) throw BundleException("Invalid ANIP cache file.")

        val formatVersion = input.readInt()
        if (formatVersion != FORMAT_VERSION) throw BundleException("Unsupported ANIP cache format: $formatVersion")

        val headerSize = input.readInt()
        if (headerSize !in 1..MAX_HEADER_SIZE_BYTES) throw BundleException("Invalid ANIP cache header size: $headerSize")

        val content = ByteArray(headerSize).also(input::readFully).toString(Charsets.UTF_8)
        return runCatching {
            json.decodeFromString<BundleHeader>(content)
        }.getOrElse {
            throw BundleException("Invalid ANIP cache header.", it)
        }
    }

    private fun writeCache(openForWrite: () -> ParcelFileDescriptor, header: BundleHeader, archive: ByteArray) {
        val headerContent = json.encodeToString(header).toByteArray()

        ParcelFileDescriptor.AutoCloseOutputStream(openForWrite()).use { stream ->
            // A caller-provided descriptor does not truncate on open, and leftover bytes would fail the size check.
            stream.channel.truncate(0)
            DataOutputStream(stream.buffered()).apply {
                writeInt(MAGIC)
                writeInt(FORMAT_VERSION)
                writeInt(headerContent.size)
                write(headerContent)
                write(archive)
            }.flush()
        }
    }

    private fun parse(archive: ByteArray, systemVariant: SystemVariant?): IconCatalog {
        val bundle = InMemoryBundle.read(archive, maxExtractedSizeBytes, maxArchiveEntries)
        return resourceManifestParser.parse(bundle, systemVariant)
    }
}