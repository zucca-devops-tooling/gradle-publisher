package dev.zucca_ops.helpers
import org.gradle.api.Project
import java.io.ByteArrayOutputStream

class GitHelper(private val project: Project, private val gitFolder: String) {

    private val POINTER = "&"
    private val SEPARATOR = "#"

    private fun getBranchForRevision(rev: Int): String? {
        val gitOutput = ByteArrayOutputStream()

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
        val decorate = listOf(
            "prefix=", // Avoid the `(` prefix on references
            "suffix=", // Avoid the `)` suffix on references
            "separator=$SEPARATOR",
            "pointer=$POINTER",
        )

        return decorate.joinToString(",")
    }

    private fun extractBranchName(output: String): String? {
        val tagsRef = "refs/tags/*"
        println("revision output:$output")

        val onlyBranches = if (output.contains(POINTER)) output.substringAfter(POINTER) else output

        return onlyBranches.split(SEPARATOR)
            .filter { it.contains("/") } // These are branches
            .filter { it != tagsRef } // We don't consider tags
            .map { it.substringAfter("/") } // Get rid of `origin/`
            .firstOrNull()
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