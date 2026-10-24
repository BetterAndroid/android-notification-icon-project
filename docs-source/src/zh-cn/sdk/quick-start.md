# 快速开始

> 集成 ANIP SDK 到你的项目中。

## 部署依赖

![Maven Central](https://img.shields.io/maven-central/v/com.highcapable.anip/anip-sdk?logo=apachemaven&logoColor=orange&style=flat-square)
<span style="margin-left: 5px"/>
![Maven metadata URL](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fraw.githubusercontent.com%2FHighCapable%2Fmaven-repository%2Frefs%2Fheads%2Fmain%2Frepository%2Freleases%2Fcom%2Fhighcapable%2Fanip%2Fanip-sdk%2Fmaven-metadata.xml&logo=apachemaven&logoColor=orange&label=highcapable-maven-releases&style=flat-square)
<span style="margin-left: 5px"/>
![Android Min SDK](https://img.shields.io/badge/Min%20SDK-21-orange?logo=android&style=flat-square)

ANIP SDK 的依赖发布在 **Maven Central** 和我们的公共存储库中，你可以使用如下方式配置存储库。

我们推荐使用 Kotlin DSL 作为 Gradle 构建脚本语言。

在你的项目 `build.gradle.kts` 中配置存储库。

```kotlin
repositories {
    google()
    mavenCentral()
    // (可选) 你可以添加此 URL 以使用我们的公共存储库
    // 当 Sonatype-OSS 发生故障无法发布依赖时，此存储库作为备选进行添加
    // 详情请前往：https://github.com/HighCapable/maven-repository
    // 中国大陆用户请将下方的 "raw.githubusercontent.com" 修改为 "raw.gitmirror.com"
    maven("https://raw.githubusercontent.com/HighCapable/maven-repository/main/repository/releases")
}
```

你可以使用如下方式将此模块添加到你的项目中。

### Version Catalog (推荐)

在你的项目 `gradle/libs.versions.toml` 中添加依赖。

```toml
[versions]
anip-sdk = "<version>"

[libraries]
anip-sdk = { module = "com.highcapable.anip:anip-sdk", version.ref = "anip-sdk" }
```

在你的项目 `build.gradle.kts` 中配置依赖。

```kotlin
dependencies {
    implementation(libs.anip.sdk)
}
```

请将 `<version>` 修改为此小节顶部显示的版本。

### 传统方式

在你的项目 `build.gradle.kts` 中配置依赖。

```kotlin
dependencies {
    implementation("com.highcapable.anip:anip-sdk:<version>")
}
```

请将 `<version>` 修改为此文档顶部显示的版本。

## 功能介绍

你可以 [点击这里](kdoc://anip-sdk) 查看 KDoc，或 [点击这里](branch://sdk/samples) 查看对应的演示项目来更好地了解 SDK 的使用方式。

### 创建实例

`Anip` 是 SDK 的统一创建入口，一份实例对应维护一份缓存和内存状态。你可以选择在应用启动时仅创建一个实例并在整个应用中复用。

> 示例如下

```kotlin
val anip = Anip(context)
```

如果你需要指定系统类型、缓存或远程来源，可以传入 `AnipConfig`。

> 示例如下

```kotlin
val config = AnipConfig(
    // 指定系统类型，用于合并厂商系统图标
    systemVariant = SystemVariant.MIOS,
    // 显式指定缓存目录
    cacheDirectory = context.cacheDir.resolve("your-anip-resources"),
    // 指定远程资源来源
    source = RemoteSource.GitHub(
        repository = RemoteSource.GITHUB_OFFICIAL_REPO_SLUG
    )
)
val anip = Anip(context, config)
```

`systemVariant` 默认为 `null`，此时不会加载任何系统图标。配置为 `COMMON` 时只加载 `common` 系统图标，配置为 `MIOS` (HyperOS/MIUI) 或 `COLOROS` 时，会加载 `common` 与对应厂商的系统图标。

配置对象会由当前 `Anip` 实例持续引用。修改其中的属性后，下一次更新时会直接读取最新配置，已装载到内存中的图标不会在配置变化时自动替换。

#### 使用文件描述符

如果当前场景下的缓存无法通过文件路径访问，你可以使用 `cacheDescriptor` 提供一个 `ParcelFileDescriptor`，它不能与 `cacheDirectory` 同时设置。

SDK 每次读写缓存时都会调用一次 `AnipConfig.CacheDescriptor` 打开新的文件描述符，并在使用后关闭。

可读写的文件描述符与 `cacheDirectory` 的用法相同。

> 示例如下

```kotlin
val config = AnipConfig(
    cacheDescriptor = AnipConfig.CacheDescriptor {
        context.contentResolver.openFileDescriptor(uri, "rw")
            ?: error("Failed to open the ANIP cache.")
    }
)
val anip = Anip(context, config)
```

如果你需要使用只读的文件描述符，可以设置 `isReadOnly = true`，此时只能通过缓存装载资源，不允许更新资源，它会在请求网络之前直接失败。

> 示例如下

```kotlin
val config = AnipConfig(
    cacheDescriptor = AnipConfig.CacheDescriptor(isReadOnly = true) {
        context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("Failed to open the ANIP cache.")
    }
)
val anip = Anip(context, config)
```

### 装载与更新资源

以下是 SDK 提供的装载与更新资源 API。

| API                    | 行为                                                                   |
| ---------------------- | ---------------------------------------------------------------------- |
| `reload`               | 仅从缓存装载有效的资源，不请求网络，成功返回 `true`                    |
| `fetch`                | 读取远程发布清单，资源有变化时下载并替换缓存，随后更新内存中的图标规则 |
| `getIcon` / `getIcons` | 仅查询当前实例的内存状态，返回 `null` 或空列表                         |

我们推荐在启动时先读取已有缓存，再检查是否有更新。

> 示例如下

```kotlin
anip.reload()

val cachedIcons = anip.getIcons()
val result = anip.fetch()
val latestIcons = anip.getIcons()
```

这些装载 API 均标记为 `suspend`，文件与网络操作由 SDK 调度到 IO 线程。

`timestamp` 表示最近一次成功同步的资源提交时间，默认值为 `0L`，与资源一同保存在缓存文件中。

#### 更新结果

`fetch` 返回 `Anip.FetchResult`，其中 `status` 有以下三种状态。

| 状态         | 含义                       |
| ------------ | -------------------------- |
| `SUCCESS`    | 已下载并装载新的资源       |
| `UP_TO_DATE` | 本地资源已经是最新版本     |
| `FAILED`     | 查询、下载、校验或装载失败 |

`Anip.FetchResult` 同时提供了 `isOk` 和 `message`，`isOk` 在状态不为 `FAILED` 时返回 `true`，`message` 会描述本次更新结果或失败原因。

> 示例如下

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

### 查询图标

你可以直接按包名查询单个图标，也可以按类别获取列表。

> 示例如下

```kotlin
val icon = anip.getIcon("com.example.app")
val apps = anip.getIcons(IconCategory.APP)
val games = anip.getIcons(IconCategory.GAME)
val systemIcons = anip.getIcons(IconCategory.SYSTEM)
val allIcons = anip.getIcons()
```

每个 `NotificationIcon` 提供以下数据。

- `packageName`：应用包名
- `label`：按照当前系统语言解析后的应用名称
- `availableLabels`：规则包含的全部本地化名称，类型为 `Map<Locale, String>`
- `color`：图标使用的颜色，未设置时为 `null`
- `overlay`：是否强制覆盖应用推送的所有通知图标，无论其是否为原生单色
- `contributors`：贡献者的名称列表

图标位图不会随实体列表一次性解码，单独使用时，请通过 `loadBitmap` 显式装载，解码失败会返回 `null`，成功结果会进入当前 `Anip` 实例持有的 LRU 内存缓存。

> 示例如下

```kotlin
val bitmap = anip.getIcon("com.example.app")?.loadBitmap()
```

### 创建内存快照

如果你需要长期、集中使用整套图标时，可以创建不可变的 `NotificationIconSnapshot`。

> 示例如下

```kotlin
val snapshot = anip.createSnapshot(IconCategory.ALL)
val icon = snapshot.getIcon("com.example.app")
val bitmap = snapshot.getBitmap("com.example.app")
```

快照会顺序装载所选类别中的图标，只保留成功解码的条目，并缓存全部位图实例，`memorySizeBytes` 会按实际 `Bitmap` 对象去重后统计占用，继承规则或多个包名使用同一资源时不会重复计算。

快照构建完成后将保持不变，后续调用 `fetch` 或 `reload` 不会修改已有快照。你可以先完整创建新快照，再替换旧引用。

释放快照引用后由 GC 回收，无需手动调用 `recycle` 或 `close`。

::: warning

`createSnapshot` 会显式承担整套图标的装载内存成本，如果你只需要偶尔获取少量图标，请直接使用 `NotificationIcon.loadBitmap`。

:::

### 配置远程来源

`RemoteSource` 提供以下远程来源模板。

| 模板   | 行为                                                        |
| ------ | ----------------------------------------------------------- |
| GitHub | 从 GitHub 存储库最新 Release 读取资源清单，默认使用官方仓库 |
| GitLab | 从 GitLab 项目最新 Release 读取资源清单，支持自定义地址     |
| Static | 在基础地址后追加 `anip-release.json`                        |
| Custom | 直接使用完整的资源清单地址                                  |

> 示例如下

```kotlin
val github = RemoteSource.GitHub("owner/repository")
val gitLab = RemoteSource.GitLab("group/project")
val static = RemoteSource.Static("https://cdn.example.com/anip")
val custom = RemoteSource.Custom("https://resources.example.com/anip-release.json")

config.source = custom
```

在自定义来源时，其需要提供符合 ANIP 规范的 `anip-release.json` 与对应的资源 Bundle。

#### 自定义请求地址

每个模板都可以传入 `RemoteSource.UrlResolver`，它会在请求资源清单和 Bundle 前接收原始 URL，并返回最终请求地址。

> 示例如下

```kotlin
val source = RemoteSource.GitHub(
    urlResolver = RemoteSource.UrlResolver { sourceUrl, _, _ ->
        "https://some.domain/$sourceUrl"
    }
)
```

如果清单请求的 `tag` 为 `null`，Bundle 请求则会同时提供 TAG 与附件名称，因此也可以分别映射到自建 CDN。

> 示例如下

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

回调中应进行静态地址拼接，你不能在回调中执行网络或耗时请求。

返回空白或非法 URL 时将直接导致失败。

:::

### 缓存与多进程

未指定缓存时，SDK 使用应用缓存目录下的 `anip-bundle` 文件，你可以通过 `cacheDirectory` 更改所在目录或使用 `cacheDescriptor` 提供文件描述符。

如果你正在 Xposed 模块中使用，如需让模块进程与宿主进程共享资源，可以由调用方设置双方均可访问的同一目录，负责联网的进程执行 `fetch`，其他进程在收到刷新通知后执行 `reload`，或者在两个进程都能获得联网权限时，每个进程都持有自己的缓存目录并通知对方更新即可。

SDK 不包含任何权限处理功能，你需要自行处理文件的授权与跨进程同步功能。

::: warning

`1.0.0` 版本的 SDK 在缓存目录中保存的解压目录已不再被读取，更新后需要重新执行 `fetch`。SDK 不会自动删除旧版缓存目录，如有需要，你可以自行删除应用缓存目录下的整个 `anip-icon-resources` 目录，如果设置过 `cacheDirectory`，则删除其中的 `releases` 目录以及 `source`、`timestamp` 文件。

:::