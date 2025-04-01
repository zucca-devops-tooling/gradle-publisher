package dev.zucca_ops.helpers

import dev.zucca_ops.configuration.PluginConfiguration
import org.gradle.api.Project

class VersionResolver(private val project: Project, private val configuration: PluginConfiguration) {

    private val gitHelper: GitHelper = GitHelper(project, configuration.gitFolder)

    fun getVersion(): String {
        val baseVersion = getProjectVersion()

        if (isRelease()) {
            return baseVersion
        }

        return "$baseVersion-${getEscapedBranchName()}-SNAPSHOT"
    }

    private fun getEscapedBranchName(): String {
        return gitHelper.getBranch().replace("/", "-")
    }

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

    private fun getProjectVersion(): String {
        return project.version.toString()
    }

}