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
package com.highcapable.anip.sdk.internal.bundle.dto

import kotlinx.serialization.Serializable

/**
 * Metadata stored in front of the bundle archive inside an ANIP cache file.
 * @param sourceIdentity stable identity of the resource source, from `RemoteSource.createIdentity`.
 * @param tag release tag of the bundle.
 * @param timestamp source commit time in epoch milliseconds.
 * @param size size of the archive only, without the header.
 * @param sha256 lowercase SHA-256 of the archive only, without the header.
 */
@Serializable
internal data class BundleHeader(
    val sourceIdentity: String,
    val tag: String,
    val timestamp: Long,
    val size: Long,
    val sha256: String
)