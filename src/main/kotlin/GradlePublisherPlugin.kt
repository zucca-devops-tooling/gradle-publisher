package dev.zucca_ops

import dev.zucca_ops.configuration.PluginConfiguration
import dev.zucca_ops.helpers.VersionResolver
import dev.zucca_ops.repositories.RepositoryPublisher
import dev.zucca_ops.repositories.RepositoryPublisherFactory
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.kotlin.dsl.configure

class GradlePublisherPlugin(): Plugin<Project> {

    override fun apply(target: Project) {
        val configuration = target.extensions.create("publisher", PluginConfiguration::class.java)

        target.afterEvaluate {
            val repositoryPublisher: RepositoryPublisher = RepositoryPublisherFactory.get(this, configuration)

            configure<PublishingExtension> {
                repositoryPublisher.configurePublishingRepository()
            }
        }
    }
}