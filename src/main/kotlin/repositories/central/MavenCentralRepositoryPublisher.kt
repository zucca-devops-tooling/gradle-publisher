package dev.zucca_ops.repositories.central

import dev.zucca_ops.helpers.VersionResolver
import dev.zucca_ops.repositories.BaseRepositoryPublisher
import dev.zucca_ops.repositories.RepositoryConstants
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import java.io.FileNotFoundException
import java.net.URL

class MavenCentralRepositoryPublisher(private val project: Project, private val versionResolver: VersionResolver,
                                      private val gradleCommand: String):
    BaseRepositoryPublisher(project, versionResolver) {
    override fun configurePublishingRepository() {
        super.configurePublishingRepository()

        project.tasks.named("publish").configure {
            if (isPublishable()) {
                // Disable the normal publish logic
                enabled = false

                // Dynamically register a rerouter task
                project.tasks.register("reroutePublishToMavenCentral") {
                    group = "publishing"
                    description = "Auto-reroutes publish to $gradleCommand"
                    dependsOn(gradleCommand)
                }

                // Now make publish depend on the reroute task
                dependsOn("reroutePublishToMavenCentral")

                project.logger.lifecycle("⚙️ Routing 'publish' to '$gradleCommand'")
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

    override fun isPublishable(): Boolean {
        return !versionResolver.isRelease() || !artifactAlreadyPublished()
    }

    override fun shouldSign(): Boolean {
        return true
    }

    override fun registerRepository(repositoryHandler: RepositoryHandler) {
        repositoryHandler.mavenCentral()
    }

}