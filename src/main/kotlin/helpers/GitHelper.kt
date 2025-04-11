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
        val gitOutput = ByteArrayOutputStream()

        val gitArgs =
            listOf(
                "--git-dir=$gitFolder/.git",
                "log",
                rev.toString(),
                "--pretty=%(decorate:${getDecoratorString()})",
            )

        println("gitargs $gitArgs")

        project.exec {
            executable = "git"
            args = gitArgs
            standardOutput = gitOutput
        }

        val output = gitOutput.toString().trim()
        return extractBranchName(output)
    }

    /**
     * Builds a custom Git decoration string to control formatting of Git ref names.
     */
    private fun getDecoratorString(): String {
        val decorate =
            listOf(
                "prefix=", // Avoid the `(` prefix on references
                "suffix=", // Avoid the `)` suffix on references
                "separator=$separator",
                "pointer=$pointer",
            )

        return decorate.joinToString(",")
    }

    private fun extractBranchName(output: String): String? {
        val tagsRef = "refs/tags/*"
        println("revision output:$output")

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
        println("comparing " + branch + " with " + getMainBranchName())
        return getMainBranchName() == branch || listOf("main", "master").contains(branch)
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
        val gitOutput = ByteArrayOutputStream()

        // Try symbolic-ref method
        project.exec {
            executable = "git"
            args = listOf("--git-dir=$gitFolder/.git", "symbolic-ref", "refs/remotes/origin/HEAD")
            standardOutput = gitOutput
            isIgnoreExitValue = true
        }

        val symbolicOutput = gitOutput.toString().trim()
        if (symbolicOutput.startsWith("refs/remotes/origin/")) {
            return symbolicOutput.substringAfterLast("/")
        }

        // Fallback: git remote show origin
        gitOutput.reset()
        project.exec {
            executable = "git"
            args = listOf("remote", "show", "origin")
            standardOutput = gitOutput
            isIgnoreExitValue = true
        }

        val remoteOutput = gitOutput.toString().trim()
        val match = Regex("HEAD branch: (\\S+)").find(remoteOutput)
        if (match != null) return match.groupValues[1]

        // Fallback: common CI env vars
        val envVars = System.getenv()
        return envVars["GITHUB_BASE_REF"]
            ?: envVars["GITHUB_REF_NAME"]
            ?: envVars["CI_DEFAULT_BRANCH"]
    }
}
