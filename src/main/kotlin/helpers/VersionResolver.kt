/*
 * Copyright 2024 the original author or authors.
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

class VersionResolver(
    private val project: Project,
    private val configuration: PluginConfiguration,
) {
    private val gitHelper: GitHelper = GitHelper(project, configuration.gitFolder)

    fun getVersion(): String {
        val baseVersion = getProjectVersion()

        if (isRelease()) {
            return baseVersion
        }

        return "$baseVersion-${getEscapedBranchName()}-SNAPSHOT"
    }

    private fun getEscapedBranchName(): String = gitHelper.getBranch().replace("/", "-")

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

    private fun getProjectVersion(): String = project.version.toString()
}
