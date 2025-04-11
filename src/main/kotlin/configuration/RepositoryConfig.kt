/*
 * Copyright 2025 the original author or authors.
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
package dev.zuccaops.configuration

/**
 * Configuration class for a single environment (dev or prod).
 *
 * This is used within the `publisher` block to define repository-specific settings.
 *
 * Example:
 * ```kotlin
 * publisher {
 *     dev {
 *         target = "https://example.com/snapshots"
 *         usernameProperty = "DEV_USER"
 *         passwordProperty = "DEV_PASS"
 *         sign = false
 *     }
 * }
 * ```
 *
 * @property target The URL or keyword for the repository. Examples:
 * - `"local"` to publish to local Maven
 * - `"mavenCentral"` to use flying-gradle-plugin
 * - `"nexus"` for OSSRH/Sonatype (requires `customGradleCommand`)
 * - or any full repository URL
 *
 * @property usernameProperty Name of the Gradle property (e.g. from `gradle.properties` or environment) that holds the repository username.
 * @property passwordProperty Name of the Gradle property that holds the password/token for authentication.
 * @property customGradleCommand Optional. The name of a custom task to be triggered instead of the default `publish` (e.g., `closeAndReleaseStagingRepositories`)
 * @property sign Whether or not this environment should sign artifacts. Defaults to `true`.
 *
 * @author Guido Zuccarelli
 */
open class RepositoryConfig {
    var target: String = Defaults.TARGET
    var usernameProperty: String = Defaults.USER_PROPERTY
    var passwordProperty: String = Defaults.PASS_PROPERTY
    var customGradleCommand: String? = null
    var sign: Boolean = true
}
