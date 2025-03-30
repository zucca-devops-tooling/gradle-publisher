package com.zucca.helpers
import org.gradle.api.Project
import java.io.ByteArrayOutputStream

class GitHelper(private val gitFolderProvider: () -> String, private val project: Project) {

    private val POINTER = "&"
    private val SEPARATOR = "#"

    private fun getBranchForRevision(rev: Int): String? {
        val gitOutput = ByteArrayOutputStream()
        val gitFolder = gitFolderProvider()

        val gitArgs = listOf(
            "--git-dir=$gitFolder/.git",
            "log",
            rev.toString(),
            "--pretty=%(decorate:${getDecoratorString()})"
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

    private fun getDecoratorString(): String {
        val jenkinsRef = "PR/*"
        val tagsRef = "refs/tags/*"

        val decorate = listOf(
            "prefix=", // Avoid the `(` prefix on references
            "suffix=", // Avoid the `)` suffix on references
            "separator=$SEPARATOR",
            "pointer=$POINTER",
            // "exclude=$jenkinsRef,$tagsRef" // Ignore tags and Jenkins generated branches
        )

        return decorate.joinToString(",")
    }

    private fun extractBranchName(output: String): String? {
        println("revision output:$output")
        println("contains$POINTER?")
        if (output.contains(POINTER)) {
            val onlyBranches = output.substringAfter(POINTER)
            println("substring after pointer $onlyBranches")

            return onlyBranches.split(SEPARATOR)
                .map { it.substringAfter("/") }
                .firstOrNull()
        }

        return null
    }

    fun getBranch(): String {
        var branch = getBranchForRevision(-1) // Based on current commit

        if (branch == null) {
            branch = getBranchForRevision(-2) // Try with previous commit in case of missing branch
        }

        return branch ?: "HEAD"
    }

    fun isMainBranch(branch: String): Boolean {
        println("comparing " + branch + " with " + getMainBranchName())
        return getMainBranchName() == branch || listOf("main", "master").contains(branch)
    }

    private fun getMainBranchName(): String? {
        val gitOutput = ByteArrayOutputStream()
        val gitFolder = gitFolderProvider()

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