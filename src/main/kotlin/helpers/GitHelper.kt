package com.zucca.helpers
import org.gradle.api.Project
import java.io.ByteArrayOutputStream

class GitHelper(private val gitFolder: String, private val project: Project) {

    private val POINTER = "&"
    private val SEPARATOR = "#"

    private fun getBranchForRevision(rev: Int): String? {
        val gitOutput = ByteArrayOutputStream()

        val gitArgs = listOf(
            "log",
            rev.toString(),
            "--git-dir=$gitFolder/.git",
            "--pretty=%(decorate:${getDecoratorString()})"
        )

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
            "exclude=$jenkinsRef,$tagsRef" // Ignore tags and Jenkins generated branches
        )

        return decorate.joinToString(",")
    }

    private fun extractBranchName(output: String): String? {
        if (output.contains(POINTER)) {
            val onlyBranches = output.substringAfter(POINTER)

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

    fun getMainBranchName(): String? {
        val gitOutput = ByteArrayOutputStream()

        project.exec {
            executable = "git"
            args = listOf("--git-dir=$gitFolder/.git", "symbolic-ref", "refs/remotes/origin/HEAD")
            standardOutput = gitOutput
            isIgnoreExitValue = true // Prevents errors from stopping execution
        }

        val output = gitOutput.toString().trim()
        if (output.isNotEmpty()) {
            return output.substringAfterLast("/")
        }

        // Fallback method
        gitOutput.reset()
        project.exec {
            executable = "git"
            args = listOf("remote", "show", "origin")
            standardOutput = gitOutput
        }

        val remoteOutput = gitOutput.toString().trim()
        val match = Regex("HEAD branch: (\\S+)").find(remoteOutput)
        return match?.groupValues?.get(1)
    }
}