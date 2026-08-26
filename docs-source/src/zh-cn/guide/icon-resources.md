# 图标资源

> 以下内容为目前社区共同维护的通知图标资源规则，你可以对这些内容进行审阅或在参与项目一节请求适配或提交新图标适配。

<IconResources />

## 商标声明

上述资源中展示的产品名称、徽标和品牌均为其各自所有者的财产。图标资源中使用的所有公司、产品和服务名称仅用于识别、兼容性和说明目的。使用这些名称、徽标和品牌并不意味着认可。

## 结构说明

资源规则目录固定划分为如下结构，它们均位于项目的 `icons` 目录。

- 第三方应用规则位于 [icons/app](branch://icons/app/)
- 游戏规则位于 [icons/game](branch://icons/game/)
- 系统规则位于 [icons/system](branch://icons/system/)

每个目录中在没有直接的子项目时，均会存在一份 `manifest.json` 文件，其对应注册的图标资源位于 `res` 目录中。

ANIP 提供了全新的规则文件结构，下面是一份完整规则的示例。

```json
"com.example.app": {
  "label": "示例应用",
  "format": "svg",
  "color": "#4F7CF7",
  "overlay": false,
  "contributors": "peter, jane"
}
```

- `label`：应用的官方名称，支持 I18n
- `format`：图标文件的格式，必须与 `res` 目录中对应的图标文件扩展名一致
- `color`：图标使用的颜色，格式要求全大写 #RRGGBB，不能存在透明通道
- `overlay`：是否强制覆盖应用推送的所有通知图标，无论其是否为原生单色
- `contributors`：贡献者的名称列表，多个贡献者使用 `,` 分隔 (后方存在空格)

其中 `label`、`format` 和 `contributors` 字段必须存在。

`label` 的 I18n 支持格式如下，其代码采用标准的 BCP 47 语言标签，使用 `-` 分隔语言和地区。

```json
"label": {
  "en": "Example",
  "zh-CN": "示例应用"
}
```

同样地，这份规则必须在 `res` 目录中存在 `com.example.app.svg` 文件与其对应，格式为包名作为文件名，`format` 作为扩展名。

一般情况下，文件名及扩展名需要为全小写。

为了应对老版本格式中 Base64 体积膨胀和渠道包重复适配图标产生的资源复制性浪费，ANIP 提供了 `target` 参数来继承其他包名的图标资源，避免重复资源占用空间。

下面是一份使用 `target` 的规则示例。

```json
"com.example.app.foo": {
  "target": "com.example.app",
  "label": "示例应用 (渠道服)"
}
```

在这个示例中，`com.example.app.foo` 的图标资源将继承 `com.example.app` 的图标资源，只要声明了 `target` 参数，`res` 目录中就不需要存在 `com.example.app.foo.svg` 文件，其余参数均通过继承的方式获取，你也可以覆写它们，但请注意 `format` 参数的覆写是无效的，将会被自动丢弃。

想要为图标资源贡献内容吗？请前往 [准备提交](../contribute/submit.md) 立即参与贡献。

## 使用说明

ANIP 的所有图标资源免费、开放、依靠贡献者进行维护，任何人都可以在遵守 Apache-2.0 协议的前提下使用、修改、分发本项目的资源，你可以查看 [这里](../about/adopters.md) 来了解目前正在使用 ANIP 的项目。

每次贡献提交的图标都会自动进行打包，最新的图标资源位于当前存储库的 [Releases](releases://) 页面中，标签为每次提交的 SHA-1 哈希值，文件名为类似 `anip-bundle-<SHA1>.zip` 的压缩包，压缩包内格式如下。

```
anip-bundle-<SHA1>.zip
├─ app/
├─ game/
└─ system/
```

我们不建议直接通过 Raw 使用项目的资源，而是直接拉取最新的 Release 压缩包进行使用，或者使用 ANIP 提供的官方 Android 依赖库 (目前尚未发布)。