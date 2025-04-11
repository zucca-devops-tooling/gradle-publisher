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
package dev.zuccaops.repositories

import dev.zuccaops.configuration.PluginConfiguration
import dev.zuccaops.configuration.RepositoryConfig
import dev.zuccaops.helpers.VersionResolver
import dev.zuccaops.repositories.central.CustomMavenCentralRepositoryPublisher
import dev.zuccaops.repositories.central.MavenCentralRepositoryPublisher
import dev.zuccaops.repositories.local.LocalRepositoryPublisher
import dev.zuccaops.repositories.remote.RemoteRepositoryPublisher
import org.gradle.api.Project

object RepositoryPublisherFactory {
    fun get(
        project: Project,
        configuration: PluginConfiguration,
    ): RepositoryPublisher {
        val versionResolver = VersionResolver(project, configuration)
        val repositoryConfig: RepositoryConfig = if (versionResolver.isRelease()) configuration.prod else configuration.dev

        val repositoryAuthenticator = RepositoryAuthenticator(project, configuration)

        return when (repositoryConfig.target) {
            RepositoryConstants.LOCAL_TARGET_COMMAND -> LocalRepositoryPublisher(project, versionResolver)
            RepositoryConstants.MAVEN_CENTRAL_COMMAND -> MavenCentralRepositoryPublisher(project, versionResolver, repositoryAuthenticator)
            RepositoryConstants.CUSTOM_MAVEN_CENTRAL_COMMAND ->
                CustomMavenCentralRepositoryPublisher(
                    project,
                    versionResolver,
                    repositoryConfig.customGradleCommand!!,
                )
            else -> RemoteRepositoryPublisher(project, versionResolver, repositoryAuthenticator, configuration)
        }
    }
}
