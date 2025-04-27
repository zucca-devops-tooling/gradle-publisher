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

import dev.zuccaops.configuration.Defaults
import dev.zuccaops.helpers.VersionResolver
import dev.zuccaops.helpers.skipTasks
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.dsl.RepositoryHandler

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
class NexusRepositoryPublisher(
    private val project: Project,
    private val versionResolver: VersionResolver,
    private var gradleCommand: String,
) : SonatypeRepositoryPublisher(project, versionResolver) {
    /**
     * Configures the publishing logic by disabling all regular publish tasks
     * and rerouting the `publish` task to the user-defined `gradleCommand`.
     *
     * Only runs this configuration if the artifact is deemed publishable.
     */
    override fun configurePublishingRepository() {
        super.configurePublishingRepository()

        if (isPublishable()) {
            if (gradleCommand.isBlank()) {
                project.logger.info("Gradle command not configured. Using Nexus default: '${Defaults.NEXUS_GRADLE_COMMAND}'")
                gradleCommand = Defaults.NEXUS_GRADLE_COMMAND
            }

            if (!project.tasks.names.contains(gradleCommand)) {
                project.logger.error("❌ Gradle task '$gradleCommand' not found")

                if (gradleCommand == Defaults.NEXUS_GRADLE_COMMAND) {
                    project.logger.error("Make sure the Nexus Publish Plugin is applied and the task is registered")
                }

                throw GradleException("Could not find Gradle task '$gradleCommand'. Please check your configuration or plugin setup.")
            }

            // Disable other publish task than nexus ones
            val nonNexusPublishTaskSelector: (Task) -> Boolean = {
                it.name.startsWith("publish") && it.name.contains("To") && it.name != gradleCommand
            }
            project.skipTasks(nonNexusPublishTaskSelector, "Disabling conflicting publish tasks (Sonatype rerouting in effect):")

            // Dynamically register a rerouter task
            val rerouteTask =
                project.tasks.register("reroutePublishToNexus") {
                    group = "publishing"
                    description = "Redirects 'publish' to custom task '$gradleCommand' for Nexus publishing"
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
     * Always enables signing for this repository type (Sonatype-style publish).
     *
     * @return true (signing is required)
     */
    override fun shouldSign(): Boolean = true // Sonatype repository requires GPG signing, always enabled

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
