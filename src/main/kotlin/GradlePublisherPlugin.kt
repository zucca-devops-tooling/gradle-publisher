package com.zucca

import com.zucca.configuration.PluginConfiguration
import com.zucca.core.RepositoryAuthenticator
import com.zucca.core.RepositoryManager
import com.zucca.core.VersionManager
import com.zucca.helpers.GitHelper
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.credentials
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
        val gradleVersion = target.gradle.gradleVersion.substringBefore(".").toInt()

        target.plugins.apply("maven-publish")

        target.tasks.named("publish") {
            dependsOn(target.tasks.named("build"))
        }

        fun configure() {
            target.configure<PublishingExtension> {
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
                        credentials(PasswordCredentials::class) {
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
                target.tasks.named("publishMavenPublicationToMyMavenRepo").configure {
                    onlyIf {
                        repositoryManager.shouldPublish()
                    }
                }
            }
        }

        if (gradleVersion < 8) {
            // older versions of gradle don't have this behaviour by default
            target.afterEvaluate {
                configure()
            }
        } else {
            configure()
        }
    }
}