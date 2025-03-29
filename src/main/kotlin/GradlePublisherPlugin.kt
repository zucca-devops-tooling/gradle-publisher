package com.zucca

import com.zucca.configuration.PluginConfiguration
import com.zucca.core.RepositoryAuthenticator
import com.zucca.core.RepositoryManager
import com.zucca.core.VersionManager
import com.zucca.helpers.GitHelper
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get

class GradlePublisherPlugin(): Plugin<Project> {

    override fun apply(target: Project) {
        val configuration = target.extensions.create("publisher", PluginConfiguration::class.java)
        val gitHelper = GitHelper(configuration.gitFolder, target)
        val versionManager = VersionManager(configuration.releaseBranchPatterns, gitHelper, target)
        val repositoryAuthenticator =
            RepositoryAuthenticator(configuration.usernameProperty, configuration.passwordProperty, target)
        val repositoryManager = RepositoryManager(
            configuration.prodRepoUrl,
            configuration.devRepoUrl,
            repositoryAuthenticator,
            versionManager,
            target
        )

        target.plugins.apply("maven-publish")

        target.tasks.named("publish") {
            dependsOn(target.tasks.named("build"))
        }

        target.afterEvaluate {
            configure<PublishingExtension> {
                publications {
                    create<MavenPublication>("maven") {
                        groupId = target.group.toString()
                        artifactId = target.name
                        from(target.components["java"])
                        version = versionManager.getVersion()
                    }
                }
                repositories{
                    maven{
                        url = target.uri(repositoryManager.getUrl())
                        credentials {
                            username = repositoryAuthenticator.getUsername()
                            password = repositoryAuthenticator.getPassword()
                        }
                        metadataSources {
                            mavenPom()
                            artifact()
                        }
                    }
                }

                target.version = versionManager.getVersion()
                target.tasks.withType(PublishToMavenRepository::class.java).configureEach {
                    onlyIf {
                        repositoryManager.shouldPublish()
                    }
                }
            }
        }
    }
}