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

/**
 * Default configuration values used throughout the Gradle Publisher plugin.
 *
 * These values serve as fallbacks in case users don't override them in their plugin configuration.
 *
 * @property TARGET Default repository target. `"local"` will publish to Maven Local.
 * @property USER_PROPERTY Default Gradle property name used to fetch repository username.
 * @property PASS_PROPERTY Default Gradle property name used to fetch repository password.
 * @property GIT_FOLDER Default path to the root Git folder. `"."` means the current project directory.
 * @property RELEASE_BRANCH_REGEXES Default regex patterns used to determine if a branch is a release branch.
 * @property ALTER_PROJECT_VERSION Default behaviour the plugin modifies the project version on non-release branches
 * @property SHADOW_JAR Default publication type if true changes from java to shadowJar
 * If a branch matches any of these patterns, it's considered a release:
 * - `release/1.2.3`
 * - `v1.2.3`
 *
 * @author Guido Zuccarelli
 */
object Defaults {
    const val TARGET = "local"
    const val USER_PROPERTY = "mavenUsername"
    const val PASS_PROPERTY = "mavenPassword"
    const val GIT_FOLDER = "."
    const val ALTER_PROJECT_VERSION = true
    const val SHADOW_JAR = false
    const val NEXUS_GRADLE_COMMAND = "closeAndReleaseStagingRepositories"
    val RELEASE_BRANCH_REGEXES: List<String> = emptyList()
}
