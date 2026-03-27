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
    private val ignoredRefs = setOf("HEAD", "grafted")

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
        val candidates = mutableListOf<Pair<Int, String>>()

        output
            .split(separator)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { ref ->
                when {
                    ref.startsWith(tagsRefPrefix) || ref in ignoredRefs -> Unit
                    ref.startsWith("HEAD$pointer") ->
                        normalizeBranchRef(ref.substringAfter(pointer))?.let {
                            candidates += 0 to it
                        }
                    ref.startsWith("origin/HEAD$pointer") || ref.startsWith("refs/remotes/origin/HEAD$pointer") ->
                        normalizeBranchRef(ref.substringAfter(pointer))?.let {
                            candidates += 3 to it
                        }
                    ref.contains(pointer) ->
                        normalizeBranchRef(ref.substringAfter(pointer))?.let {
                            candidates += 3 to it
                        }
                    else ->
                        normalizeBranchRef(ref)?.let {
                            val priority =
                                if (ref.startsWith("origin/") || ref.startsWith("refs/remotes/origin/")) {
                                    2
                                } else {
                                    1
                                }
                            candidates += priority to it
                        }
                }
            }

        return candidates
            .sortedBy { it.first }
            .map { it.second }
            .firstOrNull()
    }

    private fun normalizeBranchRef(ref: String): String? {
        val trimmed = ref.trim()

        if (trimmed.isEmpty() || trimmed in ignoredRefs || trimmed.startsWith("tag: ")) {
            return null
        }

        return when {
            trimmed.startsWith("refs/heads/") -> trimmed.removePrefix("refs/heads/")
            trimmed.startsWith("refs/remotes/origin/HEAD") -> null
            trimmed.startsWith("refs/remotes/origin/") -> trimmed.removePrefix("refs/remotes/origin/")
            trimmed.startsWith("origin/HEAD") -> null
            trimmed.startsWith("origin/") -> trimmed.removePrefix("origin/")
            else -> trimmed
        }
    }

    /**
     * Attempts to resolve the branch name of the current commit or a previous one.
     */
    fun getBranch(): String {
        val ciBranch = getBranchByCIEnv()
        if (ciBranch != null) {
            project.logger.info("Detected branch from CI environment: $ciBranch")
            return ciBranch
        }

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
    fun getBranchByCIEnv(): String? = getBranchByCIEnv(System.getenv())

    @VisibleForTesting
    internal fun getBranchByCIEnv(envVars: Map<String, String>): String? {
        project.logger.debug("Checking env vars to detect current branch")

        envVars["GITHUB_HEAD_REF"]
            ?.takeIf { it.isNotBlank() }
            ?.let {
                project.logger.debug("`GITHUB_HEAD_REF` found")
                return it
            }

        envVars["GITHUB_REF"]
            ?.takeIf { it.startsWith("refs/heads/") }
            ?.removePrefix("refs/heads/")
            ?.let {
                project.logger.debug("`GITHUB_REF` found")
                return it
            }

        envVars["CI_MERGE_REQUEST_SOURCE_BRANCH_NAME"]
            ?.takeIf { it.isNotBlank() }
            ?.let {
                project.logger.debug("`CI_MERGE_REQUEST_SOURCE_BRANCH_NAME` found")
                return it
            }

        envVars["CI_COMMIT_BRANCH"]
            ?.takeIf { it.isNotBlank() }
            ?.let {
                project.logger.debug("`CI_COMMIT_BRANCH` found")
                return it
            }

        envVars["CI_COMMIT_REF_NAME"]
            ?.takeIf { it.isNotBlank() && envVars["CI_COMMIT_TAG"].isNullOrBlank() }
            ?.let {
                project.logger.debug("`CI_COMMIT_REF_NAME` found")
                return it
            }

        envVars["BRANCH_NAME"]
            ?.takeIf { it.isNotBlank() }
            ?.let {
                project.logger.debug("`BRANCH_NAME` found")
                return it
            }

        envVars["BITBUCKET_BRANCH"]
            ?.takeIf { it.isNotBlank() }
            ?.let {
                project.logger.debug("`BITBUCKET_BRANCH` found")
                return it
            }

        envVars["GIT_BRANCH"]
            ?.takeIf { it.isNotBlank() }
            ?.let {
                project.logger.debug("`GIT_BRANCH` found")
                return it.removePrefix("refs/heads/").removePrefix("origin/")
            }

        project.logger.debug("No envVars found for current branch")
        return null
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
