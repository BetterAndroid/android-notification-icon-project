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
package com.highcapable.anip.sdk.config

import com.highcapable.anip.sdk.type.SystemVariant
import java.io.File

/**
 * Configuration for ANIP.
 * @param systemVariant system resources to load, or null to exclude all system rules.
 * @param cacheDirectory optional cache root, defaults to `cacheDir/anip-icon-resources`.
 * @param cacheDescriptor optional single-file cache used instead of [cacheDirectory].
 * @param source the remote resource source.
 */
data class AnipConfig @JvmOverloads constructor(
    var systemVariant: SystemVariant? = null,
    var cacheDirectory: File? = null,
    var cacheDescriptor: CacheDescriptor? = null,
    var source: RemoteSource = RemoteSource.GitHub()
) {

    init {
        validate()
    }

    internal fun validate() {
        source.validate()

        require(cacheDirectory?.isFile != true) {
            "ANIP cache directory must not be a file."
        }
        require(cacheDirectory == null || cacheDescriptor == null) {
            "ANIP cache directory and cache descriptor must not be set at the same time."
        }
    }
}