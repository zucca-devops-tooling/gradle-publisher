/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.zuccaops.repositories.remote

import dev.zuccaops.configuration.PluginConfiguration
import dev.zuccaops.helpers.VersionResolver
import dev.zuccaops.repositories.BaseRepositoryPublisher
import dev.zuccaops.repositories.RepositoryAuthenticator
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import java.io.FileNotFoundException
import java.net.Authenticator
import java.net.URL

/**
 * Publisher for remote Maven repositories (e.g., Artifactory, GitHub Packages).
 *
 * Responsible for:
 * - Conditionally skipping publishing if a production version already exists (via HTTP check)
 * - Configuring Maven repository credentials dynamically
 * - Disabling Sonatype-related tasks unless explicitly configured
 *
 * @param project The Gradle project
 * @param versionResolver Utility to resolve version based on Git and environment
 * @param repositoryAuthenticator Provides credentials based on environment
 * @param configuration Plugin configuration for dev/prod targets and credentials
 *
 * @author Guido Zuccarelli
 */
class RemoteRepositoryPublisher(
    private val project: Project,
    private val versionResolver: VersionResolver,
    private val repositoryAuthenticator: RepositoryAuthenticator,
    private val configuration: PluginConfiguration,
) : BaseRepositoryPublisher(project, versionResolver) {
    override fun isPublishable(): Boolean {
        if (versionResolver.isRelease()) {
            Authenticator.setDefault(repositoryAuthenticator)

            try {
                URL(getUri()).readBytes()

                return true
            } catch (e: FileNotFoundException) {
                println("RemoteRepository: production version already published")
                return false
            }
        }

        return true
    }

    override fun shouldSign(): Boolean = if (versionResolver.isRelease()) configuration.prod.sign else configuration.dev.sign

    /**
     * Configures the Maven repository for publishing using the credentials and target URL
     * defined for the current environment.
     *
     * Also disables Sonatype-specific tasks that may have been applied by other plugins.
     *
     * @param repositoryHandler Gradle's repository handler where the Maven repository is registered.
     */
    override fun registerRepository(repositoryHandler: RepositoryHandler) {
        val username: String?
        val password: String?

        if (versionResolver.isRelease()) {
            username = repositoryAuthenticator.getProdUsername()
            password = repositoryAuthenticator.getProdPassword()
        } else {
            username = repositoryAuthenticator.getDevUsername()
            password = repositoryAuthenticator.getDevPassword()
        }

        repositoryHandler.maven {
            this.url = project.uri(getRepoUrl())
            if (username != null && password != null) {
                credentials {
                    this.username = username
                    this.password = password
                }
            }
            metadataSources {
                mavenPom()
                artifact()
            }
        }

        project.tasks
            .matching { it.name.contains("ToSonatypeRepository") || it.name.contains("SonatypeStaging") }
            .configureEach {
                onlyIf {
                    logger.lifecycle("❌ Skipping Nexus task: $name (disabled by publisher plugin)")
                    false
                }
            }
    }

    private fun getRepoUrl(): String {
        if (versionResolver.isRelease()) {
            println("RemoteRepository: returning prod target url")
            return configuration.prod.target
        }

        println("RemoteRepository: returning dev target url")
        return configuration.dev.target
    }

    /**
     * Builds the expected artifact URI used to verify if it already exists.
     * This is used in the `isPublishable` logic.
     *
     * @return The full URL of the artifact path.
     */
    private fun getUri(): String {
        val group = project.group.toString().replace(".", "/")
        val name = project.name.replace(".", "/")

        val baseUrl = getRepoUrl().let { if (it.endsWith("/")) it else "$it/" }
        return "$baseUrl$group/$name"
    }
}
