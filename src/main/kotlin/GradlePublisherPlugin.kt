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
package dev.zuccaops

import dev.zuccaops.configuration.PluginConfiguration
import dev.zuccaops.helpers.isPublishRequested
import dev.zuccaops.repositories.RepositoryPublisher
import dev.zuccaops.repositories.RepositoryPublisherFactory
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * The main entry point for the Gradle Publisher plugin.
 *
 * This plugin:
 * - Registers the `publisher` extension for user configuration.
 * - Automatically applies the `maven-publish` plugin.
 * - Ensures the `publish` task depends on `build`.
 * - Delegates publishing logic to an environment-aware [RepositoryPublisher].
 *
 * @author Guido Zuccarelli
 */
class GradlePublisherPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.logger.lifecycle("Applying Gradle Publisher Plugin to project " + target.name)
        val configuration = target.extensions.create("publisher", PluginConfiguration::class.java)

        target.logger.info("Applying 'maven-publish' plugin")
        target.plugins.apply("maven-publish")

        target.tasks.named("publish") {
            logger.debug("Ensuring 'publish' depends on 'assemble'")
            dependsOn(target.tasks.named("assemble"))
        }

        target.afterEvaluate {
            logger.info("Gradle Publisher Plugin configuration: $configuration")
            val repositoryPublisher: RepositoryPublisher = RepositoryPublisherFactory.get(this)
            repositoryPublisher.setProjectVersion()

            if (project.isPublishRequested()) {
                repositoryPublisher.configurePublishingRepository()
            }
        }
    }
}
