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
package dev.zuccaops.repositories

/**
 * Common contract for all repository publisher strategies.
 *
 * Each implementation is responsible for configuring the `publishing` block dynamically,
 * including repository URL, credentials, and task overrides if necessary.
 *
 * @author Guido Zuccarelli
 */
interface RepositoryPublisher {
    /**
     * Applies the repository-specific publishing configuration to the project.
     */
    fun configurePublishingRepository()

    /**
     * Sets project.version for every gradle task
     */
    fun setProjectVersion()
}
