/*
 * Copyright 2025 GuidoZuccarelli
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
import dev.zuccaops.helpers.publisherConfiguration
import dev.zuccaops.helpers.skipTasks
import dev.zuccaops.repositories.BaseRepositoryPublisher
import dev.zuccaops.repositories.RepositoryAuthenticator
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.internal.impldep.com.google.common.annotations.VisibleForTesting
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
 *
 * @author Guido Zuccarelli
 */
class RemoteRepositoryPublisher(
    private val project: Project,
    private val versionResolver: VersionResolver,
    private val repositoryAuthenticator: RepositoryAuthenticator,
) : BaseRepositoryPublisher(project, versionResolver) {
    private val configuration: PluginConfiguration = project.publisherConfiguration()

    override fun isPublishable(): Boolean {
        if (versionResolver.isRelease()) {
            project.logger.info("Checking if artifact exists at: ${getUri()}")
            Authenticator.setDefault(repositoryAuthenticator)

            try {
                URL(getUri()).readBytes()
                project.logger.lifecycle("Production version already published, skipping tasks")
                return false
            } catch (e: FileNotFoundException) {
                project.logger.lifecycle("Production version not published yet, proceeding with publication")
                return true
            }
        }

        project.logger.lifecycle("Snapshot version detected, proceeding with publication")
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
            url = project.uri(getRepoUrl())
            project.logger.info("Setting url: $url to Publication Repository")
            if (username != null && password != null) {
                credentials {
                    this.username = username
                    this.password = password
                }
            } else {
                project.logger.warn("No credentials found for this publication")
            }
            metadataSources {
                mavenPom()
                artifact()
            }
        }

        // Disable nexus tasks in case the nexus plugin its plugin is applied
        val nexusTaskSelector: (Task) -> Boolean = {
            it.name.contains("ToSonatypeRepository") || it.name.contains("SonatypeStaging")
        }
        project.skipTasks(nexusTaskSelector, "Disabling nexus tasks (by publisher plugin):")
    }

    private fun getRepoUrl(): String {
        if (versionResolver.isRelease()) {
            return configuration.prod.target
        }

        return configuration.dev.target
    }

    /**
     * Builds the expected artifact URI used to verify if it already exists.
     * This is used in the `isPublishable` logic.
     *
     * @return The full URL of the artifact path.
     */
    @VisibleForTesting
    fun getUri(): String {
        val group = project.group.toString().replace(".", "/")
        val name = project.name.replace(".", "/")
        val version = versionResolver.getVersion()

        val baseUrl = getRepoUrl().let { if (it.endsWith("/")) it else "$it/" }
        return "$baseUrl$group/$name/$version"
    }
}
