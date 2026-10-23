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
package com.highcapable.anip.sdk.internal.icon

import com.highcapable.anip.sdk.internal.bundle.BundlePath

/**
 * Manifest and resource directories that contribute icons to a catalog.
 * @param path the directory path inside a bundle.
 */
internal enum class RuleSource(path: String) {
    /** Third-party app rules. */
    APP("app"),

    /** Game rules. */
    GAME("game"),

    /** System rules shared by every vendor. */
    SYSTEM_COMMON("system/common"),

    /** HyperOS and MIUI system rules. */
    SYSTEM_MIOS("system/mios"),

    /** ColorOS system rules. */
    SYSTEM_COLOROS("system/coloros");

    /** Directory of this source inside a bundle. */
    val relativePath = BundlePath.parse(path)
}