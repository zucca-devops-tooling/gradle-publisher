/*
 * Copyright 2025 GuidoZuccarelli
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.zuccaops.helpers

import dev.zuccaops.configuration.PluginConfiguration
import org.gradle.api.Project
import org.gradle.api.Task

/**
 * Disables all tasks in the project matching the given selector.
 *
 * @param taskSelector Predicate function to select which tasks to disable.
 * @param headerText Message to display when tasks are disabled.
 */
fun Project.skipTasks(
    taskSelector: (Task) -> Boolean,
    headerText: String,
) {
    val matchingTasks = tasks.matching(taskSelector)
    val firstTaskName = matchingTasks.firstOrNull()?.name

    matchingTasks.configureEach {
        if (this.name == firstTaskName) {
            logger.info("$headerText, disabling the following tasks:")
        }
        logger.info("  ⛔ ${this.name}")
        onlyIf { false }
    }
}

/**
 * Disables tasks of the specified type if the given skip condition is true.
 *
 * @param taskType The class type of tasks to disable.
 * @param skipCondition A function that determines whether to skip.
 * @param headerText Message to display when tasks are disabled.
 */
fun <T : Task> Project.skipTasksByType(
    taskType: Class<T>,
    skipCondition: () -> Boolean,
    headerText: String,
) {
    logger.debug("Checking if tasks should be disabled")

    if (skipCondition()) {
        val selector: (Task) -> Boolean = { taskType.isInstance(it) }
        skipTasks(selector, headerText)
    }
}

/**
 * Checks if the publish task has been explicitly requested for this project.
 *
 * @return true if publishing this project was requested, false otherwise.
 */
fun Project.isPublishRequested(): Boolean {
    val requestedTasks = gradle.startParameter.taskNames
    logger.debug("Requested tasks: {}", requestedTasks)
    logger.info("Checking if publish was called")

    return requestedTasks.any { it == "publish" || it == "$path:publish" }
}

/**
 * Provides convenient, typed access to the 'publisher' extension configuration.
 *
 * @return The [PluginConfiguration] instance registered with the project.
 * @throws IllegalStateException if the 'publisher' extension is not found.
 */
fun Project.publisherConfiguration(): PluginConfiguration = this.extensions.getByType(PluginConfiguration::class.java)
