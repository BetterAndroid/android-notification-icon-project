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

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.highcapable.anip.sdk.config.AnipConfig
import com.highcapable.anip.sdk.config.RemoteSource
import com.highcapable.anip.sdk.internal.bundle.BundleRelease
import com.highcapable.anip.sdk.internal.exception.BundleException
import com.highcapable.anip.sdk.internal.source.ResourceReleaseClient
import com.highcapable.anip.sdk.type.IconCategory
import com.highcapable.anip.sdk.type.SystemVariant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * Device-side tests for the public ANIP SDK surface and its Android resource behavior.
 */
@RunWith(AndroidJUnit4::class)
class AnipInstrumentedTest {

    private lateinit var cacheDirectory: File
    private lateinit var fixture: TestBundleFixture

    /** Creates an isolated cache root before each test. */
    @Before
    fun setUp() {
        cacheDirectory = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .cacheDir
            .resolve("anip-instrumented-test")
        cacheDirectory.deleteRecursively()
        fixture = TestBundleFixture(cacheDirectory)
    }

    /** Removes every bundle written by the current test. */
    @After
    fun tearDown() {
        cacheDirectory.deleteRecursively()
    }

    /** Verifies explicit cache restoration, filtering, inheritance, i18n, and bitmap loading. */
    @Test
    fun loadsCachedCatalogThroughPublicApi() = runBlocking {
        fixture.installValidRelease()

        val anip = createAnip(SystemVariant.MIOS)
        Assert.assertTrue(anip.getIcons().isEmpty())
        Assert.assertTrue(anip.reload())

        Assert.assertEquals(TestBundleFixture.TIMESTAMP, anip.timestamp)
        Assert.assertEquals(
            listOf(TestBundleFixture.APP_PACKAGE, TestBundleFixture.TARGET_PACKAGE),
            anip.getIcons(IconCategory.APP).map { it.packageName }
        )
        Assert.assertEquals(
            listOf(TestBundleFixture.GAME_PACKAGE),
            anip.getIcons(IconCategory.GAME).map { it.packageName }
        )
        Assert.assertEquals(
            listOf(TestBundleFixture.COMMON_PACKAGE, TestBundleFixture.MIOS_PACKAGE),
            anip.getIcons(IconCategory.SYSTEM).map { it.packageName }
        )
        Assert.assertEquals(5, anip.getIcons().size)
        Assert.assertNull(anip.getIcon("com.example.missing"))

        val appIcon = anip.getIcon(TestBundleFixture.APP_PACKAGE)
        val targetIcon = anip.getIcon(TestBundleFixture.TARGET_PACKAGE)
        val gameIcon = anip.getIcon(TestBundleFixture.GAME_PACKAGE)

        Assert.assertNotNull(appIcon)
        Assert.assertNotNull(targetIcon)
        Assert.assertNotNull(gameIcon)
        appIcon ?: return@runBlocking
        targetIcon ?: return@runBlocking
        gameIcon ?: return@runBlocking

        Assert.assertEquals(0xFF123456.toInt(), appIcon.color)
        Assert.assertTrue(appIcon.overlay)
        Assert.assertEquals(listOf("alice", "bob"), appIcon.contributors)
        Assert.assertEquals(TestBundleFixture.APP_LABEL_EN, appIcon.availableLabels[Locale.ENGLISH])
        Assert.assertEquals(TestBundleFixture.APP_LABEL_ZH_CN, appIcon.availableLabels[Locale.SIMPLIFIED_CHINESE])

        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
            Assert.assertEquals(TestBundleFixture.APP_LABEL_ZH_CN, appIcon.label)
        } finally {
            Locale.setDefault(originalLocale)
        }

        Assert.assertEquals(TestBundleFixture.TARGET_LABEL, targetIcon.label)
        Assert.assertEquals(appIcon.color, targetIcon.color)
        Assert.assertEquals(appIcon.overlay, targetIcon.overlay)
        Assert.assertEquals(appIcon.contributors, targetIcon.contributors)

        val appBitmap = appIcon.loadBitmap()
        Assert.assertNotNull(appBitmap)
        Assert.assertSame(appBitmap, targetIcon.loadBitmap())
        Assert.assertNotNull(gameIcon.loadBitmap())
    }

    /** Verifies that reload reads mutable configuration and keeps the loaded catalog when the cache file is damaged. */
    @Test
    fun reloadsCurrentCacheAndPreservesCatalogOnFailure() = runBlocking {
        fixture.installValidRelease()

        val config = AnipConfig(cacheDirectory = cacheDirectory)
        val anip = createAnip(config = config)

        Assert.assertTrue(anip.getIcons().isEmpty())
        Assert.assertTrue(anip.reload())
        Assert.assertTrue(anip.getIcons(IconCategory.SYSTEM).isEmpty())
        Assert.assertNull(anip.getIcon(TestBundleFixture.COMMON_PACKAGE))
        Assert.assertNull(anip.getIcon(TestBundleFixture.MIOS_PACKAGE))

        config.systemVariant = SystemVariant.COMMON
        Assert.assertTrue(anip.reload())
        Assert.assertEquals(listOf(TestBundleFixture.COMMON_PACKAGE), anip.getIcons(IconCategory.SYSTEM).map { it.packageName })
        Assert.assertNull(anip.getIcon(TestBundleFixture.MIOS_PACKAGE))

        config.systemVariant = SystemVariant.MIOS
        Assert.assertTrue(anip.reload())
        Assert.assertEquals(
            listOf(TestBundleFixture.COMMON_PACKAGE, TestBundleFixture.MIOS_PACKAGE),
            anip.getIcons(IconCategory.SYSTEM).map { it.packageName }
        )

        config.systemVariant = SystemVariant.COLOROS
        Assert.assertTrue(anip.reload())
        Assert.assertEquals(
            listOf(TestBundleFixture.COMMON_PACKAGE, TestBundleFixture.COLOROS_PACKAGE),
            anip.getIcons(IconCategory.SYSTEM).map { it.packageName }
        )

        fixture.truncateCache()
        Assert.assertFalse(anip.reload())
        Assert.assertNotNull(anip.getIcon(TestBundleFixture.APP_PACKAGE))
        Assert.assertNotNull(anip.getIcon(TestBundleFixture.COLOROS_PACKAGE))
        Assert.assertNotNull(anip.getIcon(TestBundleFixture.APP_PACKAGE)?.loadBitmap())

        fixture.writeAppManifest(TestBundleFixture.UPDATED_APP_LABEL_EN)
        fixture.installCache()
        Assert.assertTrue(anip.reload())
        Assert.assertEquals(
            TestBundleFixture.UPDATED_APP_LABEL_EN,
            anip.getIcon(TestBundleFixture.APP_PACKAGE)?.availableLabels?.get(Locale.ENGLISH)
        )
        Assert.assertEquals(TestBundleFixture.TIMESTAMP, anip.timestamp)
    }

    /** Verifies the empty state and local reload result before any bundle has been cached. */
    @Test
    fun returnsEmptyStateWhenCacheDoesNotExist() = runBlocking {
        val anip = createAnip()

        Assert.assertEquals(0L, anip.timestamp)
        Assert.assertTrue(anip.getIcons().isEmpty())
        Assert.assertNull(anip.getIcon(TestBundleFixture.APP_PACKAGE))
        Assert.assertFalse(anip.reload())

        val snapshot = anip.createSnapshot()
        Assert.assertTrue(snapshot.icons.isEmpty())
        Assert.assertEquals(0L, snapshot.memorySizeBytes)
    }

    /** Verifies that changing the canonical source cannot expose another source's memory or disk cache. */
    @Test
    fun isolatesLoadedResourcesBySource() = runBlocking {
        fixture.installValidRelease()
        val config = AnipConfig(cacheDirectory = cacheDirectory)
        val anip = createAnip(config = config)

        Assert.assertTrue(anip.reload())
        Assert.assertFalse(anip.getIcons().isEmpty())

        config.source = RemoteSource.Custom("https://resources.example.com/anip-release.json")
        Assert.assertEquals(0L, anip.timestamp)
        Assert.assertTrue(anip.getIcons().isEmpty())
        Assert.assertFalse(anip.reload())

        config.source = RemoteSource.GitHub()
        Assert.assertEquals(TestBundleFixture.TIMESTAMP, anip.timestamp)
        Assert.assertFalse(anip.getIcons().isEmpty())
    }

    /** Verifies category filtering, alias sharing, memory accounting, and independence from later reloads. */
    @Test
    fun createsImmutableBitmapSnapshot() = runBlocking {
        fixture.installValidRelease()
        val anip = createAnip()
        Assert.assertTrue(anip.reload())

        val snapshot = anip.createSnapshot(IconCategory.APP)
        val appIcon = snapshot.getIcon(TestBundleFixture.APP_PACKAGE)
        val appBitmap = snapshot.getBitmap(TestBundleFixture.APP_PACKAGE)

        Assert.assertEquals(
            listOf(TestBundleFixture.APP_PACKAGE, TestBundleFixture.TARGET_PACKAGE),
            snapshot.icons.map { it.packageName }
        )
        Assert.assertNotNull(appIcon)
        Assert.assertNotNull(appBitmap)
        appIcon ?: return@runBlocking
        appBitmap ?: return@runBlocking
        Assert.assertSame(appBitmap, snapshot.getBitmap(appIcon))
        Assert.assertSame(appBitmap, snapshot.getBitmap(TestBundleFixture.TARGET_PACKAGE))
        Assert.assertSame(appBitmap, appIcon.loadBitmap())
        Assert.assertEquals(appBitmap.allocationByteCount.toLong(), snapshot.memorySizeBytes)
        Assert.assertNull(snapshot.getIcon(TestBundleFixture.GAME_PACKAGE))
        Assert.assertNull(snapshot.getBitmap(TestBundleFixture.GAME_PACKAGE))

        fixture.writeAppManifest(TestBundleFixture.UPDATED_APP_LABEL_EN)
        fixture.installCache()
        Assert.assertTrue(anip.reload())
        val updatedSnapshot = anip.createSnapshot(IconCategory.APP)

        Assert.assertEquals(TestBundleFixture.APP_LABEL_EN, appIcon.availableLabels[Locale.ENGLISH])
        Assert.assertEquals(
            TestBundleFixture.UPDATED_APP_LABEL_EN,
            updatedSnapshot.getIcon(TestBundleFixture.APP_PACKAGE)?.availableLabels?.get(Locale.ENGLISH)
        )
        Assert.assertSame(appBitmap, updatedSnapshot.getBitmap(TestBundleFixture.APP_PACKAGE))
    }

    /** Verifies that one damaged resource is omitted without failing the remaining snapshot. */
    @Test
    fun skipsDamagedIconWhenCreatingSnapshot() = runBlocking {
        fixture.installValidRelease()
        fixture.corruptGameIcon()
        fixture.installCache()
        val anip = createAnip(SystemVariant.COMMON)
        Assert.assertTrue(anip.reload())

        val snapshot = anip.createSnapshot()

        Assert.assertEquals(3, snapshot.icons.size)
        Assert.assertNull(snapshot.getIcon(TestBundleFixture.GAME_PACKAGE))
        Assert.assertNotNull(snapshot.getBitmap(TestBundleFixture.APP_PACKAGE))
        Assert.assertNotNull(snapshot.getBitmap(TestBundleFixture.COMMON_PACKAGE))
    }

    /** Verifies public configuration defaults, identity, and invalid path validation. */
    @Test
    fun validatesPublicConfiguration() {
        val defaultConfig = AnipConfig()

        Assert.assertNull(defaultConfig.systemVariant)
        Assert.assertNull(defaultConfig.cacheDirectory)
        Assert.assertNull(defaultConfig.cacheDescriptor)
        Assert.assertEquals(RemoteSource.GitHub(), defaultConfig.source)
        Assert.assertNull(defaultConfig.source.urlResolver)

        val anip = createAnip(config = defaultConfig)
        Assert.assertSame(defaultConfig, anip.config)

        val cacheFile = cacheDirectory.apply {
            parentFile?.mkdirs()
            writeText("not a directory")
        }
        Assert.assertThrows(IllegalArgumentException::class.java) {
            AnipConfig(cacheDirectory = cacheFile)
        }
        Assert.assertThrows(IllegalArgumentException::class.java) {
            AnipConfig(
                cacheDirectory = cacheFile.parentFile,
                cacheDescriptor = AnipConfig.CacheDescriptor { ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY) }
            )
        }
        Assert.assertThrows(IllegalArgumentException::class.java) {
            RemoteSource.GitHub("invalid")
        }
        Assert.assertThrows(IllegalArgumentException::class.java) { RemoteSource.Custom("invalid") }
    }

    /** Verifies every source template and its canonical manifest URL through simulated requests. */
    @Test
    fun resolvesEverySourceTemplate() = runBlocking {
        val bundleArchive = fixture.createBundleArchive()
        val releaseManifest = createReleaseManifest(bundleArchive, TestDownloadServer.ASSET_NAME)
        val templates: List<Pair<String, (RemoteSource.UrlResolver) -> RemoteSource>> = listOf(
            TestDownloadServer.MANIFEST_SOURCE_URL to { resolver ->
                RemoteSource.GitHub(TestDownloadServer.REPOSITORY, resolver)
            },
            "https://gitlab.com/example/group/project/-/releases/permalink/latest/downloads/anip-release.json" to { resolver ->
                RemoteSource.GitLab("example/group/project", urlResolver = resolver)
            },
            "https://static.example.com/resources/anip-release.json" to { resolver ->
                RemoteSource.Static("https://static.example.com/resources", resolver)
            },
            "https://custom.example.com/channel/current.json" to { resolver ->
                RemoteSource.Custom("https://custom.example.com/channel/current.json", resolver)
            }
        )

        templates.forEachIndexed { index, (expectedManifestUrl, createSource) ->
            TestDownloadServer(bundleArchive, releaseManifest).use { server ->
                val resolvedContexts = mutableListOf<List<String?>>()
                val resolver = RemoteSource.UrlResolver { sourceUrl, tag, assetName ->
                    resolvedContexts += listOf(sourceUrl, tag, assetName)
                    if (tag == null) server.manifestUrl else server.url
                }
                val source = createSource(resolver)
                val anip = createAnip(
                    config = AnipConfig(
                        cacheDirectory = cacheDirectory.resolve("source-template-$index"),
                        source = source
                    )
                )

                Assert.assertEquals(expectedManifestUrl, source.manifestUrl)
                Assert.assertEquals(Anip.FetchResult.Status.SUCCESS, anip.fetch().status)
                Assert.assertNotNull(anip.getIcon(TestBundleFixture.APP_PACKAGE))
                Assert.assertEquals(
                    listOf(
                        listOf(expectedManifestUrl, null, "anip-release.json"),
                        listOf(
                            expectedManifestUrl.substringBeforeLast('/') + "/${TestDownloadServer.ASSET_NAME}",
                            TestDownloadServer.TAG,
                            TestDownloadServer.ASSET_NAME
                        )
                    ),
                    resolvedContexts
                )
                Assert.assertEquals(
                    listOf(TestDownloadServer.MANIFEST_REQUEST_TARGET, TestDownloadServer.DOWNLOAD_REQUEST_TARGET),
                    server.awaitRequestTargets()
                )
            }
        }
    }

    /** Verifies an arbitrary manifest address and its relative Bundle URL without a resolver. */
    @Test
    fun fetchesFromArbitrarySourceAddress() = runBlocking {
        val bundleArchive = fixture.createBundleArchive()
        val releaseManifest = createReleaseManifest(bundleArchive, TestDownloadServer.ASSET_NAME)

        TestDownloadServer(bundleArchive, releaseManifest).use { server ->
            val anip = createAnip(
                config = AnipConfig(
                    cacheDirectory = cacheDirectory.resolve("arbitrary-source"),
                    source = RemoteSource.Custom(server.manifestUrl)
                )
            )

            Assert.assertEquals(Anip.FetchResult.Status.SUCCESS, anip.fetch().status)
            Assert.assertNotNull(anip.getIcon(TestBundleFixture.APP_PACKAGE))
            Assert.assertEquals(
                listOf(TestDownloadServer.MANIFEST_REQUEST_TARGET, TestDownloadServer.RELATIVE_DOWNLOAD_REQUEST_TARGET),
                server.awaitRequestTargets()
            )
        }
    }

    /** Verifies that one generic resolver routes both the static manifest and validated bundle. */
    @Test
    fun resolvesEveryRemoteResourceUrl() = runBlocking {
        val bundleArchive = fixture.createBundleArchive()
        val releaseManifest = createReleaseManifest(bundleArchive, TestDownloadServer.SOURCE_URL)

        TestDownloadServer(bundleArchive, releaseManifest).use { server ->
            val resolvedContexts = mutableListOf<List<String?>>()
            val anip = createAnip(
                config = AnipConfig(
                    cacheDirectory = cacheDirectory,
                    source = RemoteSource.GitHub(
                        repository = TestDownloadServer.REPOSITORY,
                        urlResolver = { sourceUrl, tag, assetName ->
                            resolvedContexts += listOf(sourceUrl, tag, assetName)
                            if (tag == null) server.manifestUrl else server.url
                        }
                    )
                )
            )
            val result = anip.fetch()

            Assert.assertEquals(Anip.FetchResult.Status.SUCCESS, result.status)
            Assert.assertNotNull(anip.getIcon(TestBundleFixture.APP_PACKAGE))
            Assert.assertEquals(
                listOf(
                    listOf(
                        TestDownloadServer.MANIFEST_SOURCE_URL,
                        null,
                        "anip-release.json"
                    ),
                    listOf(
                        TestDownloadServer.SOURCE_URL,
                        TestDownloadServer.TAG,
                        TestDownloadServer.ASSET_NAME
                    )
                ),
                resolvedContexts
            )
            Assert.assertEquals(
                listOf(TestDownloadServer.MANIFEST_REQUEST_TARGET, TestDownloadServer.DOWNLOAD_REQUEST_TARGET),
                server.awaitRequestTargets()
            )
            Assert.assertFalse(server.hasAuthorizationHeader())
        }
    }

    /** Verifies that a downloaded bundle with a mismatched manifest digest is rejected. */
    @Test
    fun rejectsMismatchedBundleDigest() = runBlocking {
        val bundleArchive = fixture.createBundleArchive()

        TestDownloadServer(bundleArchive).use { server ->
            val client = ResourceReleaseClient(
                source = RemoteSource.Custom(
                    TestDownloadServer.MANIFEST_SOURCE_URL,
                    urlResolver = { _, _, _ -> server.url }
                ),
                maxBundleSizeBytes = bundleArchive.size.toLong()
            )
            val release = BundleRelease(
                tag = TestDownloadServer.TAG,
                timestamp = TestBundleFixture.TIMESTAMP,
                assetName = TestDownloadServer.ASSET_NAME,
                downloadUrl = TestDownloadServer.SOURCE_URL,
                assetSize = bundleArchive.size.toLong(),
                sha256 = "0".repeat(64)
            )
            val failure = runCatching { client.download(release) }.exceptionOrNull()

            Assert.assertTrue(failure is BundleException)
            Assert.assertEquals("Release SHA-256 verification failed.", failure?.message)
            Assert.assertEquals(listOf(TestDownloadServer.DOWNLOAD_REQUEST_TARGET), server.awaitRequestTargets())
        }
    }

    /** Verifies that blank and malformed resolver results fail clearly without a direct-download fallback. */
    @Test
    fun rejectsInvalidResolvedDownloadUrls() = runBlocking {
        val bundleArchive = fixture.createBundleArchive()
        val release = BundleRelease(
            tag = TestDownloadServer.TAG,
            timestamp = TestBundleFixture.TIMESTAMP,
            assetName = TestDownloadServer.ASSET_NAME,
            downloadUrl = TestDownloadServer.SOURCE_URL,
            assetSize = bundleArchive.size.toLong(),
            sha256 = bundleArchive.sha256()
        )
        val invalidResults = listOf(
            " " to "The URL resolver returned an empty URL.",
            "not-a-url" to "The URL resolver returned an invalid URL."
        )

        invalidResults.forEach { (resolvedUrl, expectedMessage) ->
            val client = ResourceReleaseClient(
                source = RemoteSource.Custom(
                    TestDownloadServer.MANIFEST_SOURCE_URL,
                    urlResolver = { _, _, _ -> resolvedUrl }
                ),
                maxBundleSizeBytes = bundleArchive.size.toLong()
            )
            val failure = runCatching { client.download(release) }.exceptionOrNull()

            Assert.assertTrue(failure is BundleException)
            Assert.assertEquals(expectedMessage, failure?.message)
        }
    }

    /** Verifies that a bundle with an entry outside the bundle path rules fails to fetch and is never cached. */
    @Test
    fun rejectsBundleWithInvalidEntryName() = runBlocking {
        val invalidEntryNames = listOf("../outside.png", "/app/absolute.png", "app\\backslash.png", "app/./dot.png", "app//empty.png")

        invalidEntryNames.forEachIndexed { index, entryName ->
            val bundleArchive = fixture.createBundleArchive(extraEntryName = entryName)
            val releaseManifest = createReleaseManifest(bundleArchive, TestDownloadServer.ASSET_NAME)

            TestDownloadServer(bundleArchive, releaseManifest).use { server ->
                val anip = createAnip(
                    config = AnipConfig(
                        cacheDirectory = cacheDirectory.resolve("invalid-entry-$index"),
                        source = RemoteSource.Custom(server.manifestUrl)
                    )
                )
                val result = anip.fetch()

                Assert.assertEquals(Anip.FetchResult.Status.FAILED, result.status)
                // Android 14+ ZipInputStream rejects `..` and absolute names itself, with its own message.
                Assert.assertTrue(result.message, result.message.contains(entryName))
                Assert.assertTrue(anip.getIcons().isEmpty())
                Assert.assertEquals(0L, anip.timestamp)
                Assert.assertFalse(anip.reload())
            }
        }
    }

    /** Verifies that a writable descriptor stores a fetched bundle that a read-only descriptor can load but not fetch. */
    @Test
    fun sharesCacheThroughDescriptors() = runBlocking {
        val bundleArchive = fixture.createBundleArchive()
        val releaseManifest = createReleaseManifest(bundleArchive, TestDownloadServer.ASSET_NAME)
        val sharedFile = cacheDirectory.apply { mkdirs() }.resolve("shared-bundle")

        TestDownloadServer(bundleArchive, releaseManifest).use { server ->
            val source = RemoteSource.Custom(server.manifestUrl)
            val writer = createAnip(
                config = AnipConfig(
                    source = source,
                    cacheDescriptor = AnipConfig.CacheDescriptor {
                        ParcelFileDescriptor.open(sharedFile, ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE)
                    }
                )
            )
            val reader = createAnip(
                config = AnipConfig(
                    source = source,
                    cacheDescriptor = AnipConfig.CacheDescriptor(isReadOnly = true) {
                        ParcelFileDescriptor.open(sharedFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    }
                )
            )

            Assert.assertEquals(Anip.FetchResult.Status.SUCCESS, writer.fetch().status)
            Assert.assertEquals(
                listOf(TestDownloadServer.MANIFEST_REQUEST_TARGET, TestDownloadServer.RELATIVE_DOWNLOAD_REQUEST_TARGET),
                server.awaitRequestTargets()
            )

            Assert.assertEquals(TestBundleFixture.TIMESTAMP, reader.timestamp)
            Assert.assertTrue(reader.reload())
            Assert.assertNotNull(reader.getIcon(TestBundleFixture.APP_PACKAGE)?.loadBitmap())

            val readOnlyResult = reader.fetch()
            Assert.assertEquals(Anip.FetchResult.Status.FAILED, readOnlyResult.status)
            Assert.assertEquals("The ANIP cache is read-only.", readOnlyResult.message)
            Assert.assertNotNull(reader.getIcon(TestBundleFixture.APP_PACKAGE))
        }
    }

    /** Verifies that a damaged cache with an unchanged timestamp is downloaded again and reported as updated. */
    @Test
    fun reportsUpdateWhenDamagedCacheIsDownloadedAgain() = runBlocking {
        val bundleArchive = fixture.createBundleArchive()
        val releaseManifest = createReleaseManifest(bundleArchive, TestDownloadServer.ASSET_NAME)

        TestDownloadServer(bundleArchive, releaseManifest).use { server ->
            val source = RemoteSource.Custom(server.manifestUrl)
            fixture.installValidRelease(source)
            fixture.truncateCache()
            val anip = createAnip(config = AnipConfig(cacheDirectory = cacheDirectory, source = source))

            Assert.assertEquals(TestBundleFixture.TIMESTAMP, anip.timestamp)
            Assert.assertEquals(Anip.FetchResult.Status.SUCCESS, anip.fetch().status)
            Assert.assertEquals(
                listOf(TestDownloadServer.MANIFEST_REQUEST_TARGET, TestDownloadServer.RELATIVE_DOWNLOAD_REQUEST_TARGET),
                server.awaitRequestTargets()
            )
            Assert.assertTrue(anip.reload())
        }
    }

    /** Verifies that a failed fetch keeps the loaded catalog when the cache has disappeared. */
    @Test
    fun keepsLoadedCatalogWhenFetchFailsWithoutCache() = runBlocking {
        // Nothing listens on port 1, so the manifest request fails without a server.
        val source = RemoteSource.Custom("http://127.0.0.1:1/anip-release.json")
        fixture.installValidRelease(source)
        val anip = createAnip(config = AnipConfig(cacheDirectory = cacheDirectory, source = source))

        Assert.assertTrue(anip.reload())
        Assert.assertTrue(fixture.cacheFile.delete())

        Assert.assertEquals(Anip.FetchResult.Status.FAILED, anip.fetch().status)
        Assert.assertNotNull(anip.getIcon(TestBundleFixture.APP_PACKAGE))
        Assert.assertNotNull(anip.getIcon(TestBundleFixture.APP_PACKAGE)?.loadBitmap())
    }

    /** Verifies that files in the cache directory left by older versions are neither read nor touched. */
    @Test
    fun ignoresLegacyCacheLayout() = runBlocking {
        val bundleArchive = fixture.createBundleArchive()
        val releaseManifest = createReleaseManifest(bundleArchive, TestDownloadServer.ASSET_NAME)
        val legacyFiles = listOf(
            cacheDirectory.resolve("releases/abc1234/app/manifest.json"),
            cacheDirectory.resolve("source"),
            cacheDirectory.resolve("timestamp"),
            cacheDirectory.resolve("staging-1.tmp")
        ).onEach {
            it.parentFile?.mkdirs()
            it.writeText("legacy")
        }

        TestDownloadServer(bundleArchive, releaseManifest).use { server ->
            val anip = createAnip(config = AnipConfig(cacheDirectory = cacheDirectory, source = RemoteSource.Custom(server.manifestUrl)))

            Assert.assertEquals(0L, anip.timestamp)
            Assert.assertFalse(anip.reload())

            Assert.assertEquals(Anip.FetchResult.Status.SUCCESS, anip.fetch().status)
            Assert.assertEquals(TestBundleFixture.TIMESTAMP, anip.timestamp)
            legacyFiles.forEach { Assert.assertEquals(it.path, "legacy", it.readText()) }
        }
    }

    /** Verifies the success contract for every fetch result status. */
    @Test
    fun mapsFetchResultStatusesToIsOk() {
        Assert.assertTrue(Anip.FetchResult("updated", Anip.FetchResult.Status.SUCCESS).isOk)
        Assert.assertTrue(Anip.FetchResult("unchanged", Anip.FetchResult.Status.UP_TO_DATE).isOk)
        Assert.assertFalse(Anip.FetchResult("failed", Anip.FetchResult.Status.FAILED).isOk)
    }

    /** Creates an SDK instance backed by the current test cache. */
    private fun createAnip(
        systemVariant: SystemVariant? = null,
        config: AnipConfig = AnipConfig(systemVariant, cacheDirectory)
    ) = Anip(InstrumentationRegistry.getInstrumentation().targetContext, config)

    /** Creates a valid static release manifest for [bundleArchive]. */
    private fun createReleaseManifest(bundleArchive: ByteArray, downloadUrl: String) = """
        {
          "schemaVersion": 1,
          "tag": "${TestDownloadServer.TAG}",
          "timestamp": ${TestBundleFixture.TIMESTAMP},
          "assetName": "${TestDownloadServer.ASSET_NAME}",
          "downloadUrl": "$downloadUrl",
          "size": ${bundleArchive.size},
          "sha256": "${bundleArchive.sha256()}"
        }
        """.trimIndent()

    /** Calculates the lowercase SHA-256 expected by a release manifest. */
    private fun ByteArray.sha256() = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString(separator = "") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
}