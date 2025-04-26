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
import org.gradle.api.Project
import org.gradle.internal.impldep.com.google.common.annotations.VisibleForTesting
import java.io.ByteArrayOutputStream

/**
 * Utility class that helps resolve Git branch information for use in versioning.
 *
 * Supports reading Git references even in detached HEAD states (e.g., CI builds).
 *
 * @param project the Gradle project used to execute Git commands
 * @param gitFolder the folder where `.git` is located (typically project root)
 *
 * @author Guido Zuccarelli
 */
class GitHelper(
    private val project: Project,
    private val gitFolder: String,
) {
    private val pointer = "&"
    private val separator = "#"

    /**
     * Gets the branch name for a specific Git revision using `git log`.
     */
    private fun getBranchForRevision(rev: Int): String? {
        // Custom decorator for easier parse
        val decorate =
            listOf(
                "prefix=", // Avoid the `(` prefix on references
                "suffix=", // Avoid the `)` suffix on references
                "separator=$separator",
                "pointer=$pointer",
            )

        val gitArgs =
            listOf(
                "--git-dir=$gitFolder/.git",
                "log",
                rev.toString(),
                "--pretty=%(decorate:${decorate.joinToString(",")})",
            )

        val output = executeGitCommand(gitArgs)

        return extractBranchName(output)
    }

    private fun extractBranchName(output: String): String? {
        val tagsRefPrefix = "tag: "

        return output
            .substringAfter(pointer)
            .split(separator)
            .map { it.trim() }
            .filterNot { it.startsWith(tagsRefPrefix) || it == "HEAD" }
            .map { it.removePrefix("origin/") }
            .firstOrNull()
    }

    /**
     * Attempts to resolve the branch name of the current commit or a previous one.
     */
    fun getBranch(): String {
        var branch = getBranchForRevision(-1) // Based on current commit

        if (branch == null) {
            branch = getBranchForRevision(-2) // Try with previous commit in case of missing branch
        }

        return branch ?: "HEAD"
    }

    /**
     * Checks if the given branch name is considered a "main" branch.
     * Includes fallback to `main`, `master`, or the origin HEAD.
     */
    fun isMainBranch(branch: String): Boolean {
        val mainBranchName = getMainBranchName()
        project.logger.info("Comparing current branch '$branch' to detected default '$mainBranchName'")

        if (mainBranchName != null) {
            project.logger.info("Detected default branch is $mainBranchName")
            return mainBranchName == branch
        }
        project.logger.info("Default branch could not be detected, defaulting to main/master")

        return listOf("main", "master").contains(branch)
    }

    /**
     * Tries to determine the main branch of the repository.
     *
     * Resolution order:
     * 1. `git symbolic-ref refs/remotes/origin/HEAD`
     * 2. `git remote show origin` output
     * 3. Common CI environment variables (GITHUB_REF_NAME, etc.)
     */
    private fun getMainBranchName(): String? {
        project.logger.info("Detecting main branch")
        // Try git symbolic-ref --short refs/remotes/origin/HEAD method
        val symbolicOutput = executeGitCommand(listOf("--git-dir=$gitFolder/.git", "symbolic-ref", "refs/remotes/origin/HEAD"))
        if (symbolicOutput.startsWith("refs/remotes/origin/")) {
            project.logger.debug("Detected default branch with symbolic refs: $symbolicOutput")
            return symbolicOutput.substringAfter("refs/remotes/origin/")
        }

        // Fallback: git remote show origin
        project.logger.debug("Falling back to 'git remote show origin'")
        val remoteOutput = executeGitCommand(listOf("remote", "show", "origin"))

        val match = Regex("HEAD branch: (\\S+)").find(remoteOutput)
        if (match != null) {
            project.logger.debug("Detected default branch with remote show $remoteOutput")
            return match.groupValues[1]
        }

        // Fallback: common CI env vars
        return getDefaultBranchByCIEnv()
    }

    @VisibleForTesting
    fun getDefaultBranchByCIEnv(): String? {
        project.logger.debug("Checking env vars to detect possible default branch")
        val envVars = System.getenv()

        // Gitlab CI default branch envVar
        if (envVars.containsKey("CI_DEFAULT_BRANCH")) {
            project.logger.debug("`CI_DEFAULT_BRANCH` found")
            return envVars["CI_DEFAULT_BRANCH"]
        }

        // Jenkins might have `BRANCH_IS_PRIMARY` which tells you current branch is default one
        // TODO: Move this to isMainBranch()
        if (envVars.containsKey("BRANCH_IS_PRIMARY") && envVars["BRANCH_IS_PRIMARY"] == "true") {
            project.logger.debug("`BRANCH_IS_PRIMARY` found")
            return envVars["BRANCH_NAME"]
        }

        project.logger.debug("No envVars found")
        return null
    }

    @VisibleForTesting
    internal fun executeGitCommand(options: List<String>): String {
        project.logger.debug("Executing git command with {}", options)
        val output = ByteArrayOutputStream()

        project.exec {
            executable = "git"
            args = options
            standardOutput = output
            isIgnoreExitValue = true
        }

        return output.toString().trim()
    }
}
