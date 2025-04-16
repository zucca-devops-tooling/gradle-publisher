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
        val configuration = target.extensions.create("publisher", PluginConfiguration::class.java)

        target.plugins.apply("maven-publish")
        target.tasks.named("publish") {
            dependsOn(target.tasks.named("build"))
        }

        target.afterEvaluate {
            val repositoryPublisher: RepositoryPublisher = RepositoryPublisherFactory.get(this, configuration)

            repositoryPublisher.configurePublishingRepository()
        }
    }
}
