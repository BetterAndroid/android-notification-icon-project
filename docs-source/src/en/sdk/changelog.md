# Changelog

> The version update history of ANIP SDK is recorded here.

::: danger

We will only maintain the latest SDK version. If you are using an outdated SDK version, you voluntarily renounce any possibility of maintenance.

:::

::: warning

Time zone of version release date: **UTC+8**

:::

### 1.0.1 | 2026.10.24 &ensp;<Badge type="latest" text="latest" vertical="middle" />

- <u>**⚠️ Breaking Change:**</u> Changed the cache format, caches from older versions are no longer read and `fetch` needs to be executed again after updating
- Added `cacheDescriptor` to provide the cache through a `ParcelFileDescriptor`, with read-only support
- Added logs for icon decoding failures, unavailable caches and failed updates
- Fixed `fetch` clearing loaded icons when it failed and the cache was unavailable
- Fixed `fetch` returning `UP_TO_DATE` after downloading resources again because of a damaged cache

### 1.0.0 | 2026.09.05 &ensp;<Badge type="warning" text="stale" vertical="middle" />

- The first version is submitted to Maven