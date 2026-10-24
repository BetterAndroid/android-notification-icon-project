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

/**
 * Validated metadata for one downloadable ANIP resource bundle release.
 * @param tag release tag, stored in the cache header.
 * @param timestamp source commit time in epoch milliseconds.
 * @param assetName expected bundle attachment name.
 * @param downloadUrl direct URL of the bundle attachment.
 * @param assetSize expected attachment size in bytes.
 * @param sha256 expected SHA-256 digest published in the release manifest.
 */
internal data class BundleRelease(
    val tag: String,
    val timestamp: Long,
    val assetName: String,
    val downloadUrl: String,
    val assetSize: Long,
    val sha256: String
)