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
 * This file is created by fankes on 2026/10/24.
 */
package com.highcapable.anip.sdk.internal.bundle

import com.highcapable.anip.sdk.internal.exception.BundleException
import com.highcapable.anip.sdk.internal.utils.readLimited
import java.util.zip.ZipInputStream

/**
 * Read-only view of every file in an ANIP bundle archive, held in memory and addressed by relative path.
 *
 * Directory entries are not kept.
 * @param entries file contents keyed by their path inside the archive.
 */
internal class InMemoryBundle private constructor(private val entries: Map<BundlePath, ByteArray>) {

    companion object {

        /**
         * Reads every file entry of [archive] while enforcing entry-count and total uncompressed-size limits.
         * @param archive the bundle ZIP content.
         * @param maxExtractedSizeBytes the maximum total uncompressed size.
         * @param maxArchiveEntries the maximum number of ZIP entries.
         * @return [InMemoryBundle]
         * @throws BundleException when the archive is malformed or exceeds a limit.
         */
        fun read(archive: ByteArray, maxExtractedSizeBytes: Long, maxArchiveEntries: Int): InMemoryBundle {
            val entries = mutableMapOf<BundlePath, ByteArray>()
            var extractedSize = 0L

            ZipInputStream(archive.inputStream()).use { input ->
                generateSequence { input.nextEntry }.forEachIndexed { index, entry ->
                    if (index >= maxArchiveEntries) throw BundleException("Release archive contains too many entries.")
                    // Directory entries end with `/`, which is not part of their path.
                    val path = BundlePath.parseOrNull(entry.name.trimEnd('/'))
                        ?: throw BundleException("Invalid release archive entry: ${entry.name}")

                    val remainingSize = maxExtractedSizeBytes - extractedSize
                    if (entry.size > remainingSize) throw BundleException("Release extracted content exceeds the size limit.")

                    if (!entry.isDirectory) {
                        val content = input.readLimited(remainingSize)
                        entries[path] = content
                        extractedSize += content.size
                    }
                    input.closeEntry()
                }
            }

            return InMemoryBundle(entries)
        }
    }

    /**
     * Returns the content of the file at [path], or null when it does not exist.
     * @param path the path inside the bundle.
     * @return [ByteArray] or null.
     */
    fun read(path: BundlePath) = entries[path]

    /**
     * Returns whether a file exists at [path].
     * @param path the path inside the bundle.
     * @return [Boolean]
     */
    fun contains(path: BundlePath) = entries.containsKey(path)

    /**
     * Returns whether at least one file exists under the directory [path].
     *
     * ZIP files may omit directory entries, so a directory exists when any file is under it.
     * @param path the directory path inside the bundle.
     * @return [Boolean]
     */
    fun containsDirectory(path: BundlePath) = entries.keys.any(path::contains)
}