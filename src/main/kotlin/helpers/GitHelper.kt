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
        val tagsRef = "refs/tags/*"

        val onlyBranches = if (output.contains(pointer)) output.substringAfter(pointer) else output

        return onlyBranches
            .split(separator)
            .filter { it.contains("/") } // These are branches
            .filter { it != tagsRef } // We don't consider tags
            .map { it.substringAfter("/") } // Get rid of `origin/`
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

        if (mainBranchName != null) {
            return mainBranchName == branch
        }

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
        // Try git symbolic-ref --short refs/remotes/origin/HEAD method
        val symbolicOutput = executeGitCommand(listOf("--git-dir=$gitFolder/.git", "symbolic-ref", "refs/remotes/origin/HEAD"))
        if (symbolicOutput.startsWith("refs/remotes/origin/")) {
            return symbolicOutput.substringAfter("refs/remotes/origin/")
        }

        // Fallback: git remote show origin
        val remoteOutput = executeGitCommand(listOf("remote", "show", "origin"))

        val match = Regex("HEAD branch: (\\S+)").find(remoteOutput)
        if (match != null) return match.groupValues[1]

        // Fallback: common CI env vars
        val envVars = System.getenv()
        println(envVars)
        return envVars["GITHUB_BASE_REF"]
            ?: envVars["GITHUB_REF_NAME"]
            ?: envVars["CI_DEFAULT_BRANCH"]
    }

    @VisibleForTesting
    internal fun executeGitCommand(options: List<String>): String {
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
