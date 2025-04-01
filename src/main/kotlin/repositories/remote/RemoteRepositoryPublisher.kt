package dev.zucca_ops.repositories.remote

import dev.zucca_ops.configuration.PluginConfiguration
import dev.zucca_ops.helpers.VersionResolver
import dev.zucca_ops.repositories.RepositoryAuthenticator
import dev.zucca_ops.repositories.RepositoryPublisher
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import java.io.FileNotFoundException
import java.net.Authenticator
import java.net.URL

class RemoteRepositoryPublisher(private val project: Project,
                                private val versionResolver: VersionResolver,
                                private val repositoryAuthenticator: RepositoryAuthenticator,
                                private val configuration: PluginConfiguration) : RepositoryPublisher {

    override fun configurePublishingRepository(publishingExtension: PublishingExtension) {
        project.plugins.apply("maven-publish")

        val username: String?
        val password: String?

        if (versionResolver.isRelease()) {
            username = repositoryAuthenticator.getProdUsername()
            password = repositoryAuthenticator.getProdPassword()
        } else {
            username = repositoryAuthenticator.getDevUsername()
            password = repositoryAuthenticator.getDevPassword()
        }

        publishingExtension.repositories {
            maven {
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

        publishingExtension.publications {
            create<MavenPublication>("maven") {
                groupId = project.group.toString()
                artifactId = project.name
                from(project.components["java"])
                version = versionResolver.getVersion()
            }
        }
    }

    override fun shouldPublish(): Boolean {
        Authenticator.setDefault(repositoryAuthenticator)

        try{
            URL(getUri()).readBytes()

            return true
        } catch (e: FileNotFoundException) {
            return false
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