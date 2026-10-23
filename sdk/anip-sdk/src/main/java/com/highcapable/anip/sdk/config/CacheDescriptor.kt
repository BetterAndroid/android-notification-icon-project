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
package com.highcapable.anip.sdk.config

import android.os.ParcelFileDescriptor
import com.highcapable.anip.sdk.Anip

/**
 * Single-file ANIP cache backed by a [ParcelFileDescriptor].
 *
 * Use it when the cache cannot be addressed by a file path, such as a file shared by another process.
 * @param isReadOnly whether the descriptor can only be read, [Anip.fetch] fails without network access when true.
 * @param opener opens the cache file each time ANIP reads or writes it.
 */
class CacheDescriptor @JvmOverloads constructor(
    val isReadOnly: Boolean = false,
    private val opener: Opener
) {

    /**
     * Opens the descriptor of an ANIP cache file.
     */
    fun interface Opener {

        /**
         * Opens a new descriptor positioned at the start of the cache file.
         *
         * ANIP takes ownership of the returned descriptor and closes it after use.
         * The descriptor must be writable unless [isReadOnly] is true.
         * @return [ParcelFileDescriptor]
         */
        fun open(): ParcelFileDescriptor
    }

    /**
     * Opens a new descriptor through [opener].
     * @return [ParcelFileDescriptor]
     */
    internal fun open() = opener.open()
}