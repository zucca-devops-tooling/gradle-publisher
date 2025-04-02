package dev.zucca_ops

import dev.zucca_ops.configuration.PluginConfiguration
import dev.zucca_ops.helpers.VersionResolver
import dev.zucca_ops.repositories.RepositoryPublisher
import dev.zucca_ops.repositories.RepositoryPublisherFactory
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.kotlin.dsl.configure

class GradlePublisherPlugin(): Plugin<Project> {

    override fun apply(target: Project) {
        val configuration = target.extensions.create("publisher", PluginConfiguration::class.java)

        target.afterEvaluate {
            val versionResolver = VersionResolver(this, configuration)
            val repositoryPublisher: RepositoryPublisher = RepositoryPublisherFactory.get(this, configuration, versionResolver)

            configure<PublishingExtension> {
                repositoryPublisher.configurePublishingRepository(this)

                target.version = versionResolver.getVersion()
                target.tasks.withType(PublishToMavenRepository::class.java).configureEach {
                    onlyIf {
                        repositoryPublisher.shouldPublish()
                    }
                }
            }
        }
    }
}