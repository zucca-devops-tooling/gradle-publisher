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
package dev.zuccaops.configuration

import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

/**
 * Configuration DSL entry point for the `publisher` extension.
 *
 * Allows users to configure credentials, Git behavior, and environment-specific repository details.
 *
 * Example usage in `build.gradle.kts`:
 * ```kotlin
 * publisher {
 *     dev {
 *         target = "https://example.com/snapshots"
 *         usernameProperty = "DEV_USER"
 *         passwordProperty = "DEV_PASS"
 *     }
 *     prod {
 *         target = "https://example.com/releases"
 *         usernameProperty = "PROD_USER"
 *         passwordProperty = "PROD_PASS"
 *     }
 *     usernameProperty = "GLOBAL_USER"
 *     passwordProperty = "GLOBAL_PASS"
 *     gitFolder = "."
 *     releaseBranchPatterns = listOf("^release/\\d+\\.\\d+\\.\\d+$")
 * }
 * ```
 * @constructor Injected by Gradle with an ObjectFactory to instantiate nested configs.
 *
 * @author Guido Zuccarelli
 */
open class PluginConfiguration
    @Inject
    constructor(
        objects: ObjectFactory,
    ) {
        val dev = objects.newInstance(RepositoryConfig::class.java)
        val prod = objects.newInstance(RepositoryConfig::class.java)

        /**
         * Configure development repository.
         */
        fun dev(configure: RepositoryConfig.() -> Unit) {
            dev.configure()
        }

        /**
         * Configure production repository.
         */
        fun prod(configure: RepositoryConfig.() -> Unit) {
            prod.configure()
        }

        /** Global fallback username property (used if not defined in `dev` or `prod`) */
        var usernameProperty: String = Defaults.USER_PROPERTY

        /** Global fallback password property (used if not defined in `dev` or `prod`) */
        var passwordProperty: String = Defaults.PASS_PROPERTY

        /** Folder containing the `.git` directory (relative to project root) */
        var gitFolder: String = Defaults.GIT_FOLDER

        /** List of regex patterns to identify release branches */
        var releaseBranchPatterns: List<String> = Defaults.RELEASE_BRANCH_REGEXES

        override fun toString(): String {
            return buildString {
                appendLine("PluginConfiguration(")
                appendLine("  dev = $dev")
                appendLine("  prod = $dev")
                appendLine("  usernameProperty = $usernameProperty")
                appendLine("  passwordProperty = $passwordProperty")
                appendLine("  releaseBranchPatterns = $releaseBranchPatterns")
                appendLine("  gitFolder = $gitFolder")
                append(")")
            }
        }
    }
