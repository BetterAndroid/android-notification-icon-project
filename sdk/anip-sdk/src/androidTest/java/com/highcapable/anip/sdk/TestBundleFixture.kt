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
 * This file is created by fankes on 2026/8/30.
 */
package com.highcapable.anip.sdk

import android.graphics.Bitmap
import android.graphics.Color
import android.os.ParcelFileDescriptor
import com.highcapable.anip.sdk.config.RemoteSource
import com.highcapable.anip.sdk.internal.bundle.BundleRelease
import com.highcapable.anip.sdk.internal.bundle.BundleStore
import com.highcapable.anip.sdk.internal.cache.BitmapCache
import com.highcapable.anip.sdk.internal.manifest.ResourceManifestParser
import com.highcapable.anip.sdk.internal.utils.sha256
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Creates a minimal valid ANIP release and installs it as the cache file of an instrumentation-test cache directory.
 */
internal class TestBundleFixture(cacheDirectory: File) {

    companion object {

        const val TIMESTAMP = 1_787_986_800_000L

        const val APP_PACKAGE = "com.example.app"
        const val TARGET_PACKAGE = "com.example.app.channel"
        const val GAME_PACKAGE = "com.example.game"
        const val COMMON_PACKAGE = "android"
        const val MIOS_PACKAGE = "com.example.mios"
        const val COLOROS_PACKAGE = "com.example.coloros"

        const val APP_LABEL_EN = "Example App"
        const val UPDATED_APP_LABEL_EN = "Updated App"
        const val APP_LABEL_ZH_CN = "示例应用"
        const val TARGET_LABEL = "Example App Channel"

        /** File name of the single cache file inside a cache directory. */
        const val CACHE_FILE_NAME = "anip-bundle"

        private const val RELEASE_TAG = "abc1234"
        private const val ICON_SIZE = 24
    }

    private val releaseDirectory = cacheDirectory.resolve("fixture-release")

    /** Cache file read by an SDK instance configured with the fixture cache directory. */
    val cacheFile = cacheDirectory.resolve(CACHE_FILE_NAME)

    /** Writes all categories and drawable formats needed by the SDK parser, then installs them into [targetFile]. */
    suspend fun installValidRelease(source: RemoteSource = RemoteSource.GitHub(), targetFile: File = cacheFile) {
        writeValidRelease()
        installCache(source, targetFile)
    }

    /**
     * Packs the current fixture release and writes it into [targetFile] through the SDK cache writer.
     *
     * The release must be valid, because the writer parses it before replacing the cache.
     */
    suspend fun installCache(source: RemoteSource = RemoteSource.GitHub(), targetFile: File = cacheFile) {
        val archive = packArchive()
        val bundleStore = BundleStore(
            sourceIdentity = source.createIdentity(),
            resourceManifestParser = ResourceManifestParser(BitmapCache(maxSizeBytes = 1)),
            maxBundleSizeBytes = archive.size.toLong(),
            maxExtractedSizeBytes = Long.MAX_VALUE,
            maxArchiveEntries = Int.MAX_VALUE,
            openForRead = { ParcelFileDescriptor.open(targetFile, ParcelFileDescriptor.MODE_READ_ONLY) },
            openForWrite = {
                targetFile.parentFile?.mkdirs()
                ParcelFileDescriptor.open(targetFile, ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE)
            }
        )
        val release = BundleRelease(
            tag = RELEASE_TAG,
            timestamp = TIMESTAMP,
            assetName = "anip-bundle-$RELEASE_TAG.zip",
            downloadUrl = "",
            assetSize = archive.size.toLong(),
            sha256 = archive.sha256()
        )
        bundleStore.obtain(release, systemVariant = null, forceRefresh = true) { archive }
    }

    /** Cuts the installed cache file in half, as if a writer was interrupted. */
    fun truncateCache() {
        val content = cacheFile.readBytes()
        cacheFile.writeBytes(content.copyOf(content.size / 2))
    }

    /** Creates a downloadable archive containing the valid fixture, plus a one-byte [extraEntryName] entry when set. */
    fun createBundleArchive(extraEntryName: String? = null): ByteArray {
        writeValidRelease()

        val content = packArchive(extraEntryName)
        releaseDirectory.deleteRecursively()
        return content
    }

    /** Writes all categories and drawable formats needed by the SDK parser. */
    private fun writeValidRelease() {
        writeAppManifest(APP_LABEL_EN)
        writeManifest(
            relativePath = "game/manifest.json",
            content = """
                {
                  "$GAME_PACKAGE": {
                    "label": "Example Game",
                    "format": "svg",
                    "color": "#654321",
                    "overlay": false,
                    "contributors": "carol"
                  }
                }
            """
        )
        writeManifest(
            relativePath = "system/common/manifest.json",
            content = """
                {
                  "$COMMON_PACKAGE": {
                    "label": "Android System",
                    "format": "png",
                    "contributors": "android"
                  }
                }
            """
        )
        writeManifest(
            relativePath = "system/mios/manifest.json",
            content = """
                {
                  "$MIOS_PACKAGE": {
                    "label": "MIOS System",
                    "format": "png",
                    "contributors": "mios"
                  }
                }
            """
        )
        writeManifest(
            relativePath = "system/coloros/manifest.json",
            content = """
                {
                  "$COLOROS_PACKAGE": {
                    "label": "ColorOS System",
                    "format": "png",
                    "contributors": "coloros"
                  }
                }
            """
        )

        writePng("app/res/$APP_PACKAGE.png")
        writeSvg("game/res/$GAME_PACKAGE.svg")
        writePng("system/common/res/$COMMON_PACKAGE.png")
        writePng("system/mios/res/$MIOS_PACKAGE.png")
        writePng("system/coloros/res/$COLOROS_PACKAGE.png")
    }

    /** Packs the current fixture release into a ZIP archive. */
    private fun packArchive(extraEntryName: String? = null) = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { archive ->
            releaseDirectory.walkTopDown().filter(File::isFile).forEach { file ->
                archive.putNextEntry(ZipEntry(file.relativeTo(releaseDirectory).invariantSeparatorsPath))
                file.inputStream().use { it.copyTo(archive) }
                archive.closeEntry()
            }
            extraEntryName?.let { name ->
                archive.putNextEntry(ZipEntry(name))
                archive.write(byteArrayOf(0))
                archive.closeEntry()
            }
        }
        output.toByteArray()
    }

    /** Rewrites the application manifest with a caller-selected English label. */
    fun writeAppManifest(englishLabel: String) {
        writeManifest(
            relativePath = "app/manifest.json",
            content = """
                {
                  "$APP_PACKAGE": {
                    "label": {
                      "en": "$englishLabel",
                      "zh-CN": "$APP_LABEL_ZH_CN"
                    },
                    "format": "png",
                    "color": "#123456",
                    "overlay": true,
                    "contributors": "alice, bob"
                  },
                  "$TARGET_PACKAGE": {
                    "label": "$TARGET_LABEL",
                    "target": "$APP_PACKAGE"
                  }
                }
            """
        )
    }

    /** Replaces the game resource with malformed SVG data that still passes catalog parsing. */
    fun corruptGameIcon() {
        releaseDirectory.resolve("game/res/$GAME_PACKAGE.svg").writeText("<svg")
    }

    /** Writes a normalized JSON manifest under the fixture release. */
    private fun writeManifest(relativePath: String, content: String) {
        releaseDirectory.resolve(relativePath).apply {
            parentFile?.mkdirs()
            writeText(content.trimIndent())
        }
    }

    /** Writes a device-generated PNG with dimensions accepted by the drawable decoder. */
    private fun writePng(relativePath: String) {
        val iconFile = releaseDirectory.resolve(relativePath).apply { parentFile?.mkdirs() }
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }
        try {
            iconFile.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
    }

    /** Writes a simple SVG with dimensions accepted by AndroidSVG. */
    private fun writeSvg(relativePath: String) {
        releaseDirectory.resolve(relativePath).apply {
            parentFile?.mkdirs()
            writeText(
                """
                    <svg xmlns="http://www.w3.org/2000/svg" width="$ICON_SIZE" height="$ICON_SIZE" viewBox="0 0 $ICON_SIZE $ICON_SIZE">
                      <path fill="#FFFFFF" d="M4 4h16v16H4z"/>
                    </svg>
                """.trimIndent()
            )
        }
    }
}