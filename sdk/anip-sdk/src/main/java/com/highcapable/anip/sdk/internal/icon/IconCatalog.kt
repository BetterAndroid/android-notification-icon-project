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

import com.highcapable.anip.sdk.entity.NotificationIcon
import com.highcapable.anip.sdk.type.IconCategory

/**
 * In-memory index of parsed notification icons.
 * @param icons icons loaded from all selected rule sources.
 */
internal class IconCatalog(icons: List<NotificationIcon>) {

    private val iconsByPackage = buildMap {
        icons.forEach { icon -> put(icon.packageName, icon) }
    }

    private val icons = iconsByPackage.values.toList()

    /** Returns the icon associated with [packageName], or null when no rule exists. */
    fun find(packageName: String) = iconsByPackage[packageName]

    /** Returns icons belonging to [category] while preserving manifest insertion order. */
    fun find(category: IconCategory) = when (category) {
        IconCategory.ALL -> icons
        IconCategory.APP -> icons.filter { it.ruleSource == RuleSource.APP }
        IconCategory.GAME -> icons.filter { it.ruleSource == RuleSource.GAME }
        IconCategory.SYSTEM -> icons.filter {
            it.ruleSource != RuleSource.APP && it.ruleSource != RuleSource.GAME
        }
    }
}