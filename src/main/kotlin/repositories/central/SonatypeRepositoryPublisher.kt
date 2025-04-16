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
package dev.zuccaops.repositories.central

import dev.zuccaops.helpers.VersionResolver
import dev.zuccaops.repositories.BaseRepositoryPublisher
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactRepositoryContainer.MAVEN_CENTRAL_URL
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.internal.impldep.com.google.common.annotations.VisibleForTesting
import java.io.FileNotFoundException
import java.net.URL

/**
 * A repository publisher for Sonatype OSSRH (s01.oss.sonatype.org).
 *
 * This is used when `target = "nexus"` and a `customGradleCommand` is defined by the user.
 *
 * This publisher:
 * - Does NOT apply the Nexus plugin (due to its complexity)
 * - Disables all regular publish tasks
 * - Reroutes `publish` to a user-defined Gradle command (e.g., `closeAndReleaseStagingRepositories`)
 * - Always enforces signing
 * - Publishes using the default Maven Central configuration
 *
 * @param project the Gradle project
 * @param versionResolver logic for deciding version format
 * @param gradleCommand the command to route publishing to
 *
 * @author Guido Zuccarelli
 */
class SonatypeRepositoryPublisher(
    private val project: Project,
    private val versionResolver: VersionResolver,
    private val gradleCommand: String,
) : BaseRepositoryPublisher(project, versionResolver) {
    /**
     * Configures the publishing logic by disabling all regular publish tasks
     * and rerouting the `publish` task to the user-defined `gradleCommand`.
     *
     * Only runs this configuration if the artifact is deemed publishable.
     */
    override fun configurePublishingRepository() {
        super.configurePublishingRepository()

        if (isPublishable()) {
            project.tasks
                .matching { it.name.startsWith("publish") && it.name.contains("To") && it.name != gradleCommand }
                .configureEach {
                    enabled = false
                    logger.lifecycle("❌ Disabled publishing task: $name")
                }

            // Dynamically register a rerouter task
            val rerouteTask =
                project.tasks.register("reroutePublishToMavenCentral") {
                    group = "publishing"
                    description = "Auto-reroutes publish to $gradleCommand"
                    dependsOn(gradleCommand)
                }

            project.logger.lifecycle("⚙️ Routing 'publish' to '$gradleCommand'")

            project.tasks.named("publish").configure {
                enabled = false
                dependsOn(rerouteTask)
            }
        }
    }

    /**
     * Returns the Maven Central-style URI for this artifact, based on the
     * group, artifact name, and resolved version.
     *
     * This is used to check if the artifact is already published.
     *
     * @return the full artifact URI
     */
    @VisibleForTesting
    fun getUri(): String {
        val group = project.group.toString().replace(".", "/")
        val name = project.name.replace(".", "/")

        return MAVEN_CENTRAL_URL + group + "/" + name + "/" + versionResolver.getVersion()
    }

    /**
     * Performs a network check to determine whether the artifact has already been
     * published to the remote repository.
     *
     * @return true if the artifact exists remotely, false otherwise
     */
    private fun artifactAlreadyPublished(): Boolean {
        try {
            URL(getUri()).readBytes()

            return true
        } catch (e: FileNotFoundException) {
            return false
        }
    }

    override fun isPublishable(): Boolean = !versionResolver.isRelease() || !artifactAlreadyPublished()

    /**
     * Always enables signing for this repository type (Sonatype-style publish).
     *
     * @return true (signing is required)
     */
    override fun shouldSign(): Boolean = true

    /**
     * Configures the publishing repository to use Maven Central layout.
     *
     * Note: the actual upload is handled by the user-defined custom Gradle command.
     *
     * @param repositoryHandler the handler for configuring Maven repositories
     */
    override fun registerRepository(repositoryHandler: RepositoryHandler) {
        repositoryHandler.mavenCentral()
    }
}
