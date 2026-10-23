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
package com.highcapable.anip.sdk.internal.utils

import java.security.MessageDigest

/**
 * Encodes digest bytes as a lowercase hexadecimal string.
 * @return [String]
 */
internal fun ByteArray.asHexString() = joinToString(separator = "") { byte ->
    (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
}

/**
 * Calculates the lowercase hexadecimal SHA-256 digest of this content.
 * @return [String]
 */
internal fun ByteArray.sha256() = MessageDigest.getInstance("SHA-256").digest(this).asHexString()