package dev.zucca_ops.repositories.remote

import dev.zucca_ops.configuration.PluginConfiguration
import dev.zucca_ops.helpers.VersionResolver
import dev.zucca_ops.repositories.BaseRepositoryPublisher
import dev.zucca_ops.repositories.RepositoryAuthenticator
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import java.io.FileNotFoundException
import java.net.Authenticator
import java.net.URL

class RemoteRepositoryPublisher(private val project: Project,
                                private val versionResolver: VersionResolver,
                                private val repositoryAuthenticator: RepositoryAuthenticator,
                                private val configuration: PluginConfiguration) : BaseRepositoryPublisher(project, versionResolver) {

    override fun shouldPublish(): Boolean {
        Authenticator.setDefault(repositoryAuthenticator)

        try{
            URL(getUri()).readBytes()

            return true
        } catch (e: FileNotFoundException) {
            return false
        }
    }

    override fun registerRepository(repositoryHandler: RepositoryHandler) {
        val username: String?
        val password: String?

        if (versionResolver.isRelease()) {
            username = repositoryAuthenticator.getProdUsername()
            password = repositoryAuthenticator.getProdPassword()
        } else {
            username = repositoryAuthenticator.getDevUsername()
            password = repositoryAuthenticator.getDevPassword()
        }

        repositoryHandler.maven {
            this.url = project.uri(getRepoUrl())
            if (username != null && password != null) {
                credentials {
                    this.username = username
                    this.password = password
                }
            }
            metadataSources {
                mavenPom()
                artifact()
            }
        }
    }

    private fun getRepoUrl(): String {
        if (versionResolver.isRelease()) {
            return configuration.prod.target
        }

        return configuration.dev.target
    }

    private fun getUri(): String {
        val group = project.group.toString().replace(".", "/")
        val name = project.name.replace(".", "/")

        return getRepoUrl() + group + "/" + name
    }
}