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
package com.highcapable.anip.sdk.internal.bundle

import com.highcapable.anip.sdk.internal.exception.BundleException

/**
 * Validated relative path of a file or directory inside an ANIP bundle.
 *
 * Every instance uses `/` separators and has no leading or trailing `/`, and none of its segments is blank,
 * contains `\`, or is `.` or `..`. This is the only place that defines those rules.
 */
@JvmInline
internal value class BundlePath private constructor(private val value: String) {

    companion object {

        /**
         * Parses a `/`-separated relative path.
         * @param path the path to parse.
         * @return [BundlePath]
         * @throws BundleException when any segment is invalid.
         */
        fun parse(path: String) = parseOrNull(path) ?: throw BundleException("Invalid bundle path: $path")

        /**
         * Parses a `/`-separated relative path.
         * @param path the path to parse.
         * @return [BundlePath] or null when any segment is invalid.
         */
        fun parseOrNull(path: String) = if (path.split('/').all(::isValidSegment)) BundlePath(path) else null

        /**
         * Returns whether [name] can be used as one path segment.
         * @param name the segment to check.
         * @return [Boolean]
         */
        fun isValidSegment(name: String) = name.isNotBlank() && !name.contains('/') &&
            !name.contains('\\') && name != "." && name != ".."
    }

    /**
     * Returns the child path [name] under this path.
     * @param name one path segment.
     * @return [BundlePath]
     * @throws BundleException when [name] is not a valid segment.
     */
    fun resolve(name: String): BundlePath {
        if (!isValidSegment(name)) throw BundleException("Invalid bundle path segment: $name")
        return BundlePath("$value/$name")
    }

    /**
     * Returns whether [other] is located under this directory path.
     * @param other the path to check.
     * @return [Boolean]
     */
    fun contains(other: BundlePath) = other.value.startsWith("$value/")

    override fun toString() = value
}