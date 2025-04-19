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
package dev.zuccaops.repositories

import dev.zuccaops.helpers.VersionResolver
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.internal.impldep.com.google.common.annotations.VisibleForTesting
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import org.gradle.plugins.signing.Sign

/**
 * Abstract base class for repository publishing logic.
 *
 * Provides a common implementation for setting up a `MavenPublication`, assigning the resolved version,
 * and configuring conditional logic for publishing and signing tasks.
 *
 * Subclasses must implement:
 * - [isPublishable]: whether publishing should proceed in the current context
 * - [shouldSign]: whether signing should be enabled
 * - [registerRepository]: logic to register the appropriate Maven repository
 *
 * @param project The Gradle project
 * @param versionResolver Handles version resolution based on Git and config
 *
 * @author Guido Zuccarelli
 */
abstract class BaseRepositoryPublisher(
    private val project: Project,
    private val versionResolver: VersionResolver,
) : RepositoryPublisher {
    override fun configurePublishingRepository() {
        project.logger.lifecycle("Configuring Publishing extension")
        project.configure<PublishingExtension> {
            publications {
                project.logger.info("Creating maven publication")
                create<MavenPublication>("maven") {
                    groupId = project.group.toString()
                    artifactId = project.name
                    from(project.components["java"])
                    version = versionResolver.getVersion()
                    project.logger.debug("Configured publication [group: $groupId, artifact: $artifactId, version: $version]")
                }
            }
            project.logger.info("Registering repositories")
            registerRepository(this.repositories)

            project.version = versionResolver.getVersionForProject()

            if (!isPublishable()) {
                val publishTasks = project.tasks.withType(PublishToMavenRepository::class.java)
                publishTasks.configureEach {
                    if (this.name == publishTasks.first().name) { // only log once
                        project.logger.info("Version not publishable, disabling the following tasks:")
                    }
                    project.logger.info("  ⛔ ${this.name}")
                    onlyIf { false }
                }
            }

            if (!shouldSign()) {
                val signTasks = project.tasks.withType(Sign::class.java)
                project.tasks.withType(Sign::class.java).configureEach {
                    if (this.name == signTasks.first().name) {
                        project.logger.info("Version not signable, disabling the following tasks:")
                    }
                    project.logger.info("  ⛔ ${this.name}")
                    onlyIf { false }
                }
            }
        }
    }

    @VisibleForTesting
    abstract fun isPublishable(): Boolean

    @VisibleForTesting
    abstract fun shouldSign(): Boolean

    protected abstract fun registerRepository(repositoryHandler: RepositoryHandler)
}
