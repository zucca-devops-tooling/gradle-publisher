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

/**
 * Resolves the effective version of the project based on the current Git branch and plugin configuration.
 *
 * If the current branch matches a release pattern or is a main branch, the version is used as-is.
 * Otherwise, it appends `-<branch>-SNAPSHOT` to the project version.
 *
 * Example:
 * - `1.2.3` on `feature/foo` → `1.2.3-feature-foo-SNAPSHOT`
 * - `1.2.3` on `main` → `1.2.3`
 *
 * @param project the Gradle project
 * @param configuration the plugin configuration
 *
 * @author Guido Zuccarelli
 */
class VersionResolver(
    private val project: Project,
    private val configuration: PluginConfiguration,
) {
    private val gitHelper: GitHelper = GitHelper(project, configuration.gitFolder)

    /**
     * Returns the computed project version based on Git branch.
     * Adds `-<branch>-SNAPSHOT` if not a release branch.
     */
    fun getVersion(): String {
        val baseVersion = getProjectVersion()

        if (isRelease()) {
            return baseVersion
        }

        return "$baseVersion-${getEscapedBranchName()}-SNAPSHOT"
    }

    /** Escapes slashes in the branch name for safe use in versions. */
    private fun getEscapedBranchName(): String = gitHelper.getBranch().replace("/", "-")

    /**
     * Returns true if the current branch is a release branch.
     * Uses either `releaseBranchPatterns` or defaults to checking if branch is main.
     */
    fun isRelease(): Boolean {
        val currentBranch = gitHelper.getBranch()
        val releaseBranchPatterns = configuration.releaseBranchPatterns

        if (releaseBranchPatterns.isEmpty()) {
            if (currentBranch == "HEAD") {
                return false
            }

            return gitHelper.isMainBranch(currentBranch)
        }

        return releaseBranchPatterns.any { currentBranch.matches(Regex(it)) }
    }

    /** Returns the project version defined in `build.gradle[.kts]`. */
    private fun getProjectVersion(): String = project.version.toString()
}
