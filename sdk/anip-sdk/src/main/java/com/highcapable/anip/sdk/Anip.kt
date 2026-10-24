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
package com.highcapable.anip.sdk

import android.content.Context
import com.highcapable.anip.sdk.config.AnipConfig
import com.highcapable.anip.sdk.entity.NotificationIcon
import com.highcapable.anip.sdk.entity.NotificationIconSnapshot
import com.highcapable.anip.sdk.internal.repository.IconResourceRepository
import com.highcapable.anip.sdk.type.IconCategory

/**
 * Entry point for Android Notification Icon Project (ANIP).
 *
 * Call [reload] or [fetch] before querying locally available icons.
 * @param context the current context.
 * @param config the current configuration.
 */
class Anip @JvmOverloads constructor(context: Context, val config: AnipConfig = AnipConfig()) {

    /**
     * Result of an ANIP fetch operation.
     * @param message the human-readable result message.
     * @param status the fetch operation status.
     */
    data class FetchResult(
        val message: String,
        val status: Status
    ) {

        /**
         * Status of an ANIP fetch operation.
         */
        enum class Status {
            /** Resources were fetched and updated. */
            SUCCESS,

            /** Resources could not be fetched or loaded. */
            FAILED,

            /** Cached resources already match the latest release. */
            UP_TO_DATE
        }

        /** Whether the fetch operation completed without failure. */
        val isOk get() = status != Status.FAILED
    }

    private val repository = IconResourceRepository(context, config)

    /**
     * Timestamp of the most recently synchronized icon resource bundle.
     *
     * The value is persisted and defaults to 0 before the first successful [fetch].
     *
     * Each access reads the cache header synchronously on the calling thread. The cache is rewritten in place, so
     * reading it while [fetch] is writing the cache may briefly return 0.
     */
    val timestamp get() = repository.timestamp

    /**
     * Synchronizes and loads the latest icon resources using the current [config].
     * @return [FetchResult]
     */
    suspend fun fetch() = repository.fetch()

    /**
     * Reloads icon resources from the current local cache without performing network access.
     *
     * The currently loaded resources are preserved when no valid cached catalog is available.
     * @return [Boolean] whether cached icon resources were loaded successfully.
     */
    suspend fun reload() = repository.reload()

    /**
     * Returns the loaded icon entity for a package name, or null before [reload] or [fetch] succeeds.
     * @param packageName the app package name.
     * @return [NotificationIcon] or null.
     */
    fun getIcon(packageName: String) = repository.find(packageName)

    /**
     * Returns the loaded icon entities for list presentation, or an empty list before [reload] or [fetch] succeeds.
     * @param category the icon category, default is [IconCategory.ALL].
     * @return [List]<[NotificationIcon]>
     */
    @JvmOverloads
    fun getIcons(category: IconCategory = IconCategory.ALL) = repository.find(category)

    /**
     * Creates a complete in-memory snapshot of every successfully decoded icon in [category].
     *
     * Snapshot creation is serialized with [fetch] and [reload]. The returned [NotificationIconSnapshot] remains
     * independent of later catalog updates. It is empty when no catalog has been explicitly loaded.
     * @param category the icon category, default is [IconCategory.ALL].
     * @return [NotificationIconSnapshot]
     */
    @JvmOverloads
    suspend fun createSnapshot(category: IconCategory = IconCategory.ALL) = repository.createSnapshot(category)
}