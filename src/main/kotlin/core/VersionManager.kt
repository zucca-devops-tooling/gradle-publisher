package com.zucca.core

import com.zucca.helpers.GitHelper
import org.gradle.api.Project

class VersionManager(private val releaseBranchPatterns: List<String>, private val gitHelper: GitHelper, private val project: Project) {

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

        if (releaseBranchPatterns.isEmpty()) {
            val mainBranch = gitHelper.getMainBranchName() ?: return false

            if (currentBranch == "HEAD") {
                return false
            }

            return currentBranch == mainBranch
        }

        return releaseBranchPatterns.any { currentBranch.matches(Regex(it)) }
    }

    private fun getProjectVersion(): String {
        return project.version.toString()
    }

}