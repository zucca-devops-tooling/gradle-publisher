package dev.zucca_ops.repositories.local

import dev.zucca_ops.helpers.VersionResolver
import dev.zucca_ops.repositories.RepositoryConstants
import dev.zucca_ops.repositories.RepositoryPublisher
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import java.io.File

class LocalRepositoryPublisher(private val project: Project, private val versionResolver: VersionResolver): RepositoryPublisher {

    override fun configurePublishingRepository(publishingExtension: PublishingExtension) {
        project.plugins.apply("maven-publish")

        publishingExtension.repositories {
            mavenLocal()
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

}