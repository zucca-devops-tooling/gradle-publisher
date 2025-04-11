/*
 * Copyright 2024 the original author or authors.
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
import dev.zuccaops.repositories.RepositoryConstants
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import java.io.FileNotFoundException
import java.net.URL

class CustomMavenCentralRepositoryPublisher(
    private val project: Project,
    private val versionResolver: VersionResolver,
    private val gradleCommand: String,
) : BaseRepositoryPublisher(project, versionResolver) {
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

    private fun getUri(): String {
        val group = project.group.toString().replace(".", "/")
        val name = project.name.replace(".", "/")

        return RepositoryConstants.MAVEN_CENTRAL_URL + group + "/" + name + "/" + versionResolver.getVersion()
    }

    private fun artifactAlreadyPublished(): Boolean {
        try {
            URL(getUri()).readBytes()

            return true
        } catch (e: FileNotFoundException) {
            return false
        }
    }

    override fun isPublishable(): Boolean = !versionResolver.isRelease() || !artifactAlreadyPublished()

    override fun shouldSign(): Boolean = true

    override fun registerRepository(repositoryHandler: RepositoryHandler) {
        repositoryHandler.mavenCentral()
    }
}
