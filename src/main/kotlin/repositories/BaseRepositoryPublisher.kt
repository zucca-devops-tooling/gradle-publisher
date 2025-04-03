package dev.zucca_ops.repositories

import dev.zucca_ops.helpers.VersionResolver
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get

abstract class BaseRepositoryPublisher(private val project: Project, private val versionResolver: VersionResolver): RepositoryPublisher {

    override fun configurePublishingRepository() {
        project.plugins.apply("maven-publish")
        project.tasks.named("publish") {
            dependsOn(project.tasks.named("build"))
        }

        project.configure<PublishingExtension> {
            publications {
                create<MavenPublication>("maven") {
                    groupId = project.group.toString()
                    artifactId = project.name
                    from(project.components["java"])
                    version = versionResolver.getVersion()
                }
            }

            registerRepository(this.repositories)

            project.version = versionResolver.getVersion()
            project.tasks.withType(PublishToMavenRepository::class.java).configureEach {
                onlyIf {
                    shouldPublish()
                }
            }
        }
    }

    protected abstract fun shouldPublish(): Boolean

    protected abstract fun registerRepository(repositoryHandler: RepositoryHandler)

}