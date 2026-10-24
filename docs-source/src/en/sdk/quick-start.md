# Quick Start

> Integrate ANIP SDK into your project.

## Deploy Dependency

![Maven Central](https://img.shields.io/maven-central/v/com.highcapable.anip/anip-sdk?logo=apachemaven&logoColor=orange&style=flat-square)
<span style="margin-left: 5px"/>
![Maven metadata URL](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fraw.githubusercontent.com%2FHighCapable%2Fmaven-repository%2Frefs%2Fheads%2Fmain%2Frepository%2Freleases%2Fcom%2Fhighcapable%2Fanip%2Fanip-sdk%2Fmaven-metadata.xml&logo=apachemaven&logoColor=orange&label=highcapable-maven-releases&style=flat-square)
<span style="margin-left: 5px"/>
![Android Min SDK](https://img.shields.io/badge/Min%20SDK-21-orange?logo=android&style=flat-square)

ANIP SDK's dependency is published in **Maven Central** and our public repository.
You can use the following method to configure repositories.

We recommend using Kotlin DSL as the Gradle build script language.

Configure repositories in your project's `build.gradle.kts`.

```kotlin
repositories {
    google()
    mavenCentral()
    // (Optional) You can add this URL to use our public repository
    // When Sonatype-OSS fails and cannot publish dependencies, this repository is added as a backup
    // For details, please visit: https://github.com/HighCapable/maven-repository
    maven("https://raw.githubusercontent.com/HighCapable/maven-repository/main/repository/releases")
}
```

You can add this module to your project using the following method.

### Version Catalog (Recommended)

Add the dependency to your project's `gradle/libs.versions.toml`.

```toml
[versions]
anip-sdk = "<version>"

[libraries]
anip-sdk = { module = "com.highcapable.anip:anip-sdk", version.ref = "anip-sdk" }
```

Configure the dependency in your project's `build.gradle.kts`.

```kotlin
dependencies {
    implementation(libs.anip.sdk)
}
```

Replace `<version>` with the version displayed at the top of this section.

### Traditional Method

Configure the dependency in your project's `build.gradle.kts`.

```kotlin
dependencies {
    implementation("com.highcapable.anip:anip-sdk:<version>")
}
```

Replace `<version>` with the version displayed at the top of this document.

## Function Introduction

You can view the KDoc [click here](kdoc://anip-sdk), or [click here](branch://sdk/samples) to view the corresponding demo project to better understand how to use the SDK.

### Create an Instance

`Anip` is the unified entry point for creating the SDK. Each instance maintains one cache and one set of in-memory state. You can create a single instance when the app starts and reuse it throughout the app.

> The following example

```kotlin
val anip = Anip(context)
```

If you need to specify a system variant, cache, or remote source, pass an `AnipConfig`.

> The following example

```kotlin
val config = AnipConfig(
    // Specifies the system variant used to merge vendor system icons.
    systemVariant = SystemVariant.MIOS,
    // Explicitly specifies the cache directory.
    cacheDirectory = context.cacheDir.resolve("your-anip-resources"),
    // Specifies the remote resource source.
    source = RemoteSource.GitHub(
        repository = RemoteSource.GITHUB_OFFICIAL_REPO_SLUG
    )
)
val anip = Anip(context, config)
```

`systemVariant` defaults to `null`, which loads no system icons. `COMMON` loads only the `common` system icons, while `MIOS` (HyperOS/MIUI) and `COLOROS` load both `common` and the corresponding vendor system icons.

The current `Anip` instance keeps a reference to the configuration object. After modifying its properties, the latest configuration will be read directly during the next update. Icons already loaded into memory will not be automatically replaced when the configuration changes.

#### Use a File Descriptor

If the cache cannot be accessed through a file path in your scenario, you can provide a `ParcelFileDescriptor` through `cacheDescriptor`. It cannot be set together with `cacheDirectory`.

Each time the SDK reads or writes the cache, it calls `AnipConfig.CacheDescriptor` once to open a new file descriptor and closes it after use.

A readable and writable file descriptor works the same as `cacheDirectory`.

> The following example

```kotlin
val config = AnipConfig(
    cacheDescriptor = AnipConfig.CacheDescriptor {
        context.contentResolver.openFileDescriptor(uri, "rw")
            ?: error("Failed to open the ANIP cache.")
    }
)
val anip = Anip(context, config)
```

If you need to use a read-only file descriptor, you can set `isReadOnly = true`. In this case resources can only be loaded from the cache and cannot be updated, and an update fails directly before any network request.

> The following example

```kotlin
val config = AnipConfig(
    cacheDescriptor = AnipConfig.CacheDescriptor(isReadOnly = true) {
        context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("Failed to open the ANIP cache.")
    }
)
val anip = Anip(context, config)
```

### Load and Update Resources

The following APIs are provided by the SDK for loading and updating resources.

| API                    | Behavior                                                                                                                                |
| ---------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| `reload`               | Loads only valid resources from the cache without network requests, returning `true` on success.                                        |
| `fetch`                | Reads the remote release manifest, downloads and replaces the cache when resources have changed, then updates the in-memory icon rules. |
| `getIcon` / `getIcons` | Queries only the current instance's in-memory state, returning `null` or an empty list.                                                 |

We recommend reading the existing cache at startup before checking for updates.

> The following example

```kotlin
anip.reload()

val cachedIcons = anip.getIcons()
val result = anip.fetch()
val latestIcons = anip.getIcons()
```

These loading APIs are all marked as `suspend`, and file and network operations are dispatched by the SDK to the IO thread.

`timestamp` represents the resource commit time of the most recent successful synchronization. It defaults to `0L` and is stored in the cache file together with the resources.

#### Update Result

`fetch` returns an `Anip.FetchResult`, where `status` has the following three states.

| Status       | Meaning                                                 |
| ------------ | ------------------------------------------------------- |
| `SUCCESS`    | New resources have been downloaded and loaded.          |
| `UP_TO_DATE` | Local resources are already up to date.                 |
| `FAILED`     | Querying, downloading, verification, or loading failed. |

`Anip.FetchResult` also provides `isOk` and `message`. `isOk` returns `true` when the status is not `FAILED`, while `message` describes the update result or failure reason.

> The following example

```kotlin
when (result.status) {
    Anip.FetchResult.Status.SUCCESS -> {
        // Do something.
    }
    Anip.FetchResult.Status.UP_TO_DATE -> {
        // Do something.
    }
    Anip.FetchResult.Status.FAILED -> showError(result.message)
}
```

### Query Icons

You can query a single icon directly by package name, or obtain a list by category.

> The following example

```kotlin
val icon = anip.getIcon("com.example.app")
val apps = anip.getIcons(IconCategory.APP)
val games = anip.getIcons(IconCategory.GAME)
val systemIcons = anip.getIcons(IconCategory.SYSTEM)
val allIcons = anip.getIcons()
```

Each `NotificationIcon` provides the following data.

- `packageName`: App package name
- `label`: App name resolved according to the current system language
- `availableLabels`: All localized names included in the rule, represented as `Map<Locale, String>`
- `color`: Color used by the icon, or `null` when not set
- `overlay`: Whether to force overlay all notification icons pushed by the app, regardless of whether they are native monochrome
- `contributors`: List of contributor names

Icon bitmaps are not decoded all at once with the entity list. When using one individually, please load it explicitly via `loadBitmap`.
A decoding failure returns `null`, while a successful result enters the LRU memory cache held by the current `Anip` instance.

> The following example

```kotlin
val bitmap = anip.getIcon("com.example.app")?.loadBitmap()
```

### Create an In-Memory Snapshot

If you need to use the complete icon set intensively over a long period, you can create an immutable `NotificationIconSnapshot`.

> The following example

```kotlin
val snapshot = anip.createSnapshot(IconCategory.ALL)
val icon = snapshot.getIcon("com.example.app")
val bitmap = snapshot.getBitmap("com.example.app")
```

The snapshot loads icons in the selected category sequentially, keeps only entries that decode successfully, and caches all Bitmap instances.
`memorySizeBytes` measures usage by deduplicating actual `Bitmap` objects, so inherited rules or multiple package names using the same resource are not counted repeatedly.

The snapshot remains unchanged after construction. Later calls to `fetch` or `reload` do not modify an existing snapshot. You can finish creating a new snapshot before replacing the old reference.

After the snapshot reference is released, it is reclaimed by GC. There is no need to manually call `recycle` or `close`.

::: warning

`createSnapshot` explicitly incurs the memory cost of loading the complete icon set. If you only need to obtain a few icons occasionally, please use `NotificationIcon.loadBitmap` directly.

:::

### Configure Remote Sources

`RemoteSource` provides the following remote source templates.

| Template | Behavior                                                                                                    |
| -------- | ----------------------------------------------------------------------------------------------------------- |
| GitHub   | Reads the resource manifest from the latest GitHub repository Release; defaults to the official repository. |
| GitLab   | Reads the resource manifest from the latest GitLab project Release; supports custom host addresses.         |
| Static   | Appends `anip-release.json` after the base URL.                                                             |
| Custom   | Uses the complete resource manifest URL directly.                                                           |

> The following example

```kotlin
val github = RemoteSource.GitHub("owner/repository")
val gitLab = RemoteSource.GitLab("group/project")
val static = RemoteSource.Static("https://cdn.example.com/anip")
val custom = RemoteSource.Custom("https://resources.example.com/anip-release.json")

config.source = custom
```

When customizing sources, it needs to provide an ANIP-compliant `anip-release.json` and its corresponding resource Bundle.

#### Customize Request URLs

Every template accepts a `RemoteSource.UrlResolver`. Before requesting the resource manifest or Bundle, it receives the original URL and returns the final request URL.

> The following example

```kotlin
val source = RemoteSource.GitHub(
    urlResolver = RemoteSource.UrlResolver { sourceUrl, _, _ ->
        "https://some.domain/$sourceUrl"
    }
)
```

If `tag` is `null` for a manifest request, a Bundle request provides both tag and asset name, allowing each resource to be mapped to a self-hosted CDN separately.

> The following example

```kotlin
val source = RemoteSource.Custom(
    manifestUrl = "https://origin.example.com/anip-release.json",
    urlResolver = RemoteSource.UrlResolver { _, tag, assetName ->
        if (tag == null)
            "https://cdn.example.com/anip-release.json"
        else "https://cdn.example.com/releases/$tag/$assetName"
    }
)
```

::: warning

Static URL concatenation should be performed in the callback. You cannot execute network or time-consuming requests in the callback.

Returning a blank or invalid URL will cause failure directly.

:::

### Cache and Multiple Processes

When no cache is specified, the SDK uses the `anip-bundle` file under the app cache directory. You can change its directory through `cacheDirectory`, or provide a file descriptor through `cacheDescriptor`.

If you are using the SDK in an Xposed module and need to share resources between the module process and host process, the caller can configure one directory that both sides can access. The process responsible for networking executes `fetch`, while other processes execute `reload` after receiving a refresh notification. Or, if both processes have network access, each process can maintain its own cache directory and notify the other to update.

The SDK does not include any permission handling functions. You need to handle file authorization and cross-process synchronization yourself.

::: warning

The extracted directories saved in the cache directory by SDK version `1.0.0` are no longer read. Execute `fetch` again after updating. The SDK does not delete old cache directories automatically. If needed, you can delete the whole `anip-icon-resources` directory under the app cache directory yourself. If you have set `cacheDirectory`, delete the `releases` directory and the `source` and `timestamp` files in it instead.

:::