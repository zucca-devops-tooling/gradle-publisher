package dev.zucca_ops.repositories.central

import dev.zucca_ops.helpers.VersionResolver
import dev.zucca_ops.repositories.RepositoryConstants
import dev.zucca_ops.repositories.RepositoryPublisher
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import java.io.FileNotFoundException
import java.net.URL

class MavenCentralRepositoryPublisher(private val project: Project, private val versionResolver: VersionResolver,
                                      private val gradleCommand: String): RepositoryPublisher {
    override fun configurePublishingRepository() {
        if (!project.tasks.names.contains("publish")) {
            project.tasks.register("publish") {
                group = "publishing"
                description = "Custom placeholder for publishing to Maven Central"

                if (shouldPublish()) {
                    if (project.tasks.names.contains(gradleCommand)) {
                        dependsOn("build")
                        finalizedBy(gradleCommand)
                    }
                }
            }
        }

        project.version = versionResolver.getVersion()
        project.tasks.withType(PublishToMavenRepository::class.java).configureEach {
            onlyIf {
                shouldPublish()
            }
        }
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

    private fun shouldPublish(): Boolean {
        return !versionResolver.isRelease() || !artifactAlreadyPublished()
    }

}