package dev.zucca_ops.repositories.central

import dev.zucca_ops.helpers.VersionResolver
import dev.zucca_ops.repositories.BaseRepositoryPublisher
import dev.zucca_ops.repositories.RepositoryAuthenticator
import dev.zucca_ops.repositories.RepositoryConstants
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import java.io.FileNotFoundException
import java.net.URL
import java.util.*
import org.gradle.api.provider.Property
import org.gradle.api.file.DirectoryProperty

class MavenCentralRepositoryPublisher(private val project: Project, private val versionResolver: VersionResolver,
                                      private val repositoryAuthenticator: RepositoryAuthenticator):
    BaseRepositoryPublisher(project, versionResolver) {
    override fun configurePublishingRepository() {
        super.configurePublishingRepository()

        project.pluginManager.apply("tech.yanand.maven-central-publish")
        val encodedCredentials = encodeBasicAuth(repositoryAuthenticator.getProdUsername()!!,
            repositoryAuthenticator.getProdPassword()!!
        )
        project.extensions.configure("mavenCentral", Action<Any> {
            val clazz = this.javaClass

            val repoDir = project.layout.buildDirectory.dir("repos/bundles").get()

            clazz.getMethod("getRepoDir")
                .invoke(this)
                .let { it as DirectoryProperty }
                .set(repoDir)

            @Suppress("UNCHECKED_CAST")
            clazz.getMethod("getAuthToken")
                .invoke(this)
                .let { it as Property<String> }
                .set(encodedCredentials)
        })

        project.tasks.named("publish").configure {
            finalizedBy("publishToMavenCentralPortal")
        }

    }

    private fun encodeBasicAuth(user: String, token: String): String {
        val authString = "$user:$token"
        return Base64.getEncoder().encodeToString(authString.toByteArray(Charsets.UTF_8))
    }

    private fun getUri(): String {
        val group = project.group.toString().replace(".", "/")
        val name = project.name.replace(".", "/")

        return RepositoryConstants.MAVEN_CENTRAL_URL + group + "/" + name + "/" + versionResolver.getVersion()
    }

    private fun artifactAlreadyPublished(): Boolean {
        try{
            URL(getUri()).readBytes()

            return true
        } catch (e: FileNotFoundException) {
            return false
        }
    }

    override fun isPublishable(): Boolean {
        return !versionResolver.isRelease() || !artifactAlreadyPublished()
    }

    override fun shouldSign(): Boolean {
        return true
    }

    override fun registerRepository(repositoryHandler: RepositoryHandler) {
        repositoryHandler.maven {
            name = "Local"
            url = project.layout.buildDirectory.dir("repos/bundles").get().asFile.toURI()
        }
    }

}