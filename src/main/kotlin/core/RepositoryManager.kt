package com.zucca.core

import org.gradle.api.Project
import java.io.FileNotFoundException
import java.net.Authenticator
import java.net.URL

class RepositoryManager(private val prodRepoUrl: String, private val devRepoUrl: String, private val repositoryAuthenticator: RepositoryAuthenticator,
                        private val versionManager: VersionManager, private val project: Project
) {

    fun shouldPublish(): Boolean {
        if (versionManager.isRelease()) {
            return !artifactAlreadyPublished()
        }

        return true
    }

    fun getUrl(): String {
        if (versionManager.isRelease()) {
            return prodRepoUrl
        }

        return devRepoUrl
    }

    private fun getUri(): String {
        val group = project.group.toString().replace(".", "/")
        val name = project.name.toString().replace(".", "/")

        return getUrl() + group + "/" + name
    }

    private fun artifactAlreadyPublished(): Boolean {
        Authenticator.setDefault(repositoryAuthenticator)

        try{
            URL(getUri()).readBytes()

            return true
        } catch (e: FileNotFoundException) {
            return false
        }
    }
}