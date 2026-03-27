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

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.internal.impldep.com.google.common.annotations.VisibleForTesting
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Utility class that helps resolve Git branch information for use in versioning.
 *
 * Supports reading Git references even in detached HEAD states (e.g., CI builds).
 *
 * @param project the Gradle project used to execute Git commands
 * @param gitFolder the repository root or `.git` entry used to resolve Git metadata
 *
 * @author Guido Zuccarelli
 */
class GitHelper(
    private val project: Project,
    private val gitFolder: String,
) {
    @VisibleForTesting
    internal data class GitCommandResult(
        val output: String,
        val errorOutput: String,
        val exitCode: Int,
    )

    private val pointer = "&"
    private val separator = "#"
    private val ignoredRefs = setOf("HEAD", "grafted")
    private var gitContextValidated = false

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
            buildGitCommand(
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
        ensureGitContextAvailable()

        val ciBranch = getBranchByCIEnv()
        if (ciBranch != null) {
            project.logger.info("Detected branch from CI environment: $ciBranch")
            return ciBranch
        }

        var branch = getBranchForRevision(-1) // Based on current commit

        if (branch == null) {
            branch = getBranchForRevision(-2) // Try with previous commit in case of missing branch
        }

        return branch
            ?: throw invalidGitContext(
                "Could not resolve the current branch from CI metadata or Git history.",
                resolution =
                    "Make sure the checkout contains branch metadata and that HEAD points to a commit with branch decorations.",
            )
    }

    /**
     * Checks if the given branch name is considered a "main" branch.
     * Includes fallback to `main`, `master`, or the origin HEAD.
     */
    fun isMainBranch(branch: String): Boolean {
        ensureGitContextAvailable()

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
        val symbolicOutput = executeGitCommand(buildGitCommand("symbolic-ref", "refs/remotes/origin/HEAD"))
        if (symbolicOutput.startsWith("refs/remotes/origin/")) {
            project.logger.debug("Detected default branch with symbolic refs: $symbolicOutput")
            return symbolicOutput.substringAfter("refs/remotes/origin/")
        }

        // Fallback: git remote show origin
        project.logger.debug("Falling back to 'git remote show origin'")
        val remoteOutput = executeGitCommand(buildGitCommand("remote", "show", "origin"))

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
    internal fun ensureGitContextAvailable() {
        if (gitContextValidated) {
            return
        }

        val configuredPath = resolveConfiguredGitPath()
        val gitMetadataPath = resolveGitMetadataPath(configuredPath)

        if (!configuredPath.exists()) {
            throw invalidGitContext(
                "Configured publisher.gitFolder path does not exist.",
                resolvedPath = configuredPath,
                gitMetadataPath = gitMetadataPath,
                resolution = "Point publisher.gitFolder to the repository root or to the .git entry inside that repository.",
            )
        }

        if (configuredPath.name != ".git" && !configuredPath.isDirectory) {
            throw invalidGitContext(
                "Configured publisher.gitFolder path is not a directory.",
                resolvedPath = configuredPath,
                gitMetadataPath = gitMetadataPath,
                resolution = "Point publisher.gitFolder to the repository root or to the .git entry inside that repository.",
            )
        }

        if (!gitMetadataPath.exists()) {
            throw invalidGitContext(
                "No Git metadata was found for the configured publisher.gitFolder.",
                resolvedPath = configuredPath,
                gitMetadataPath = gitMetadataPath,
                resolution = "Run the build from a real Git checkout or update publisher.gitFolder so it targets the correct repository.",
            )
        }

        val validationCommand = buildGitCommand("rev-parse", "--is-inside-work-tree")
        val validationResult = executeGitCommandResult(validationCommand)

        if (validationResult.exitCode != 0 || validationResult.output != "true") {
            throw invalidGitContext(
                "Git repository validation failed.",
                resolvedPath = configuredPath,
                gitMetadataPath = gitMetadataPath,
                command = validationCommand,
                commandResult = validationResult,
                resolution = "Ensure the git executable is installed, on PATH, and that publisher.gitFolder points at the active checkout.",
            )
        }

        gitContextValidated = true
    }

    private fun resolveConfiguredGitPath(): File = project.file(gitFolder).canonicalFile

    private fun resolveGitMetadataPath(configuredPath: File): File =
        if (configuredPath.name == ".git") {
            configuredPath
        } else {
            File(configuredPath, ".git")
        }

    private fun buildGitCommand(vararg command: String): List<String> {
        val repositoryRoot = resolveRepositoryRoot()
        return listOf("-C", repositoryRoot.absolutePath) + command
    }

    private fun resolveRepositoryRoot(): File {
        val configuredPath = resolveConfiguredGitPath()
        return if (configuredPath.name == ".git") {
            configuredPath.parentFile ?: configuredPath
        } else {
            configuredPath
        }
    }

    private fun invalidGitContext(
        problem: String,
        resolvedPath: File = resolveConfiguredGitPath(),
        gitMetadataPath: File = resolveGitMetadataPath(resolvedPath),
        command: List<String>? = null,
        commandResult: GitCommandResult? = null,
        resolution: String,
    ): GradleException {
        val message =
            buildString {
                appendLine("Gradle Publisher requires a valid Git-aware checkout, but the Git context is unavailable or misconfigured.")
                appendLine()
                appendLine("Project: ${project.path}")
                appendLine("publisher.gitFolder: $gitFolder")
                appendLine("Resolved path: ${resolvedPath.absolutePath}")
                appendLine("Expected Git metadata: ${gitMetadataPath.absolutePath}")
                appendLine("Problem: $problem")

                if (command != null) {
                    appendLine("Git command: git ${command.joinToString(" ")}")
                }

                if (commandResult != null) {
                    appendLine("Exit code: ${commandResult.exitCode}")
                    if (commandResult.errorOutput.isNotBlank()) {
                        appendLine("stderr: ${commandResult.errorOutput}")
                    }
                    if (commandResult.output.isNotBlank()) {
                        appendLine("stdout: ${commandResult.output}")
                    }
                }

                appendLine()
                appendLine("Resolution:")
                appendLine("- Run the build from a real Git checkout.")
                appendLine("- $resolution")
                appendLine("- If you are in CI, make sure the checkout includes Git metadata instead of an exported source archive.")
            }.trim()

        project.logger.error(message)
        return GradleException(message)
    }

    @VisibleForTesting
    internal fun executeGitCommandResult(options: List<String>): GitCommandResult {
        project.logger.debug("Executing git command with {}", options)
        val output = ByteArrayOutputStream()
        val errorOutput = ByteArrayOutputStream()

        return try {
            val result =
                project.exec {
                    executable = "git"
                    args = options
                    standardOutput = output
                    this.errorOutput = errorOutput
                    isIgnoreExitValue = true
                }

            GitCommandResult(
                output = output.toString().trim(),
                errorOutput = errorOutput.toString().trim(),
                exitCode = result.exitValue,
            )
        } catch (exception: Exception) {
            GitCommandResult(
                output = output.toString().trim(),
                errorOutput =
                    errorOutput
                        .toString()
                        .trim()
                        .ifBlank { exception.message ?: exception.javaClass.simpleName },
                exitCode = -1,
            )
        }
    }

    @VisibleForTesting
    internal fun executeGitCommand(options: List<String>): String {
        return executeGitCommandResult(options).output
    }
}
