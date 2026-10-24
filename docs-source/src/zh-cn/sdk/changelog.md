# 更新日志

> 这里记录了 ANIP SDK 的版本更新历史。

::: danger

我们只会对最新的 SDK 版本进行维护，若你正在使用过时的 SDK 版本则代表你自愿放弃一切维护的可能性。

:::

### 1.0.1 | 2026.10.24 &ensp;<Badge type="latest" text="最新" vertical="middle" />

- <u>**⚠️ 破坏性更新：**</u>调整了缓存格式，旧版本缓存不再读取，更新后需要重新执行 `fetch`
- 新增 `cacheDescriptor` 用于通过 `ParcelFileDescriptor` 提供缓存，支持只读模式
- 新增图标解码失败、缓存不可用与更新失败时的日志输出
- 修复 `fetch` 失败且缓存不可用时会清空已装载图标的问题
- 修复缓存损坏后重新下载资源时 `fetch` 返回 `UP_TO_DATE` 的问题

### 1.0.0 | 2026.09.05 &ensp;<Badge type="warning" text="过旧" vertical="middle" />

- 首个版本提交至 Maven