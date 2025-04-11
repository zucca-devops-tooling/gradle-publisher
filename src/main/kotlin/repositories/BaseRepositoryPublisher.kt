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
                    isPublishable()
                }
            }
            project.tasks.matching {
                it.name in listOf("signMavenPublication", "signPluginMavenPublication")
            }.configureEach {
                onlyIf {
                    shouldSign()
                }
            }
        }
    }

    protected abstract fun isPublishable(): Boolean

    protected abstract fun shouldSign(): Boolean

    protected abstract fun registerRepository(repositoryHandler: RepositoryHandler)

}