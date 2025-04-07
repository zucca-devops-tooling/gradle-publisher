package dev.zucca_ops.repositories.local

import dev.zucca_ops.helpers.VersionResolver
import dev.zucca_ops.repositories.BaseRepositoryPublisher
import dev.zucca_ops.repositories.RepositoryConstants
import dev.zucca_ops.repositories.RepositoryPublisher
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import java.io.File

class LocalRepositoryPublisher(private val project: Project, private val versionResolver: VersionResolver):
    BaseRepositoryPublisher(project, versionResolver) {

    override fun isPublishable(): Boolean {
        if (versionResolver.isRelease()) {
            val m2Repo = File(System.getProperty("user.home"), RepositoryConstants.LOCAL_MAVEN_PATH)
            val groupPath = project.group.toString().replace('.', '/')
            val artifactId = project.name
            val version = versionResolver.getVersion()

            val artifactPath = File(m2Repo, "$groupPath/$artifactId/$version/$artifactId-$version.jar")

            return artifactPath.exists()
        }

        return true
    }

    override fun shouldSign(): Boolean {
        return false
    }


    override fun registerRepository(repositoryHandler: RepositoryHandler) {
        repositoryHandler.mavenLocal()
        repositoryHandler.removeIf{ it.name == "sonatype" }
    }

}