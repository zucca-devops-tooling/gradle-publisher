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

import org.gradle.api.Action
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
        internal var resolvedVersionInternal: String? = null
        internal var effectiveVersionInternal: String? = null

        /**
         * The fully resolved version based on branch context and plugin rules.
         *
         * This value is computed by the plugin after project evaluation.
         * Accessing it before evaluation will result in an error.
         *
         * Always returns the full version (e.g., `1.2.3-feature-X-SNAPSHOT`)
         * regardless of `alterProjectVersion`.
         */
        val resolvedVersion: String
            get() =
                resolvedVersionInternal
                    ?: error("resolvedVersion is not available yet — plugin has not been evaluated")

        /**
         * The version that was or will be applied to the project.
         *
         * - If `alterProjectVersion = true`, this will match `resolvedVersion`.
         * - If `alterProjectVersion = false`, this will return the static `project.version`.
         *
         * Computed by the plugin after evaluation.
         */
        val effectiveVersion: String
            get() =
                effectiveVersionInternal
                    ?: error("effectiveVersion is not available yet — plugin has not been evaluated")

        val dev = objects.newInstance(RepositoryConfig::class.java)
        val prod = objects.newInstance(RepositoryConfig::class.java)

        /**
         * Configure development repository.
         */
        fun dev(configure: Action<RepositoryConfig>) {
            configure.execute(dev)
        }

        /**
         * Configure production repository.
         */
        fun prod(configure: Action<RepositoryConfig>) {
            configure.execute(prod)
        }

        /** Global fallback username property (used if not defined in `dev` or `prod`) */
        var usernameProperty: String = Defaults.USER_PROPERTY

        /** Global fallback password property (used if not defined in `dev` or `prod`) */
        var passwordProperty: String = Defaults.PASS_PROPERTY

        /** Folder containing the `.git` directory (relative to project root) */
        var gitFolder: String = Defaults.GIT_FOLDER

        /** List of regex patterns to identify release branches */
        var releaseBranchPatterns: List<String> = Defaults.RELEASE_BRANCH_REGEXES

        /** If true, the plugin modifies the project version on non-release branches */
        var alterProjectVersion: Boolean = Defaults.ALTER_PROJECT_VERSION

        override fun toString(): String =
            buildString {
                appendLine("PluginConfiguration(")
                appendLine("  dev = $dev")
                appendLine("  prod = $dev")
                appendLine("  usernameProperty = $usernameProperty")
                appendLine("  passwordProperty = $passwordProperty")
                appendLine("  releaseBranchPatterns = $releaseBranchPatterns")
                appendLine("  gitFolder = $gitFolder")
                appendLine("  alterProjectVersion = $alterProjectVersion")
                append(")")
            }
    }
