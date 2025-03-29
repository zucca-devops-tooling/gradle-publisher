package com.zucca.core

import com.zucca.configuration.PluginConfiguration
import org.gradle.api.Project
import java.io.FileNotFoundException
import java.net.Authenticator
import java.net.URL

class RepositoryManager(private val pluginConfiguration: PluginConfiguration, private val repositoryAuthenticator: RepositoryAuthenticator,
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
            return pluginConfiguration.prodRepoUrl
        }

        return pluginConfiguration.devRepoUrl
    }

    private fun getUri(): String {
        val group = project.group.toString().replace(".", "/")
        val name = project.name.replace(".", "/")

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