package dev.zucca_ops.repositories

import dev.zucca_ops.configuration.PluginConfiguration
import dev.zucca_ops.configuration.RepositoryConfig
import dev.zucca_ops.helpers.VersionResolver
import dev.zucca_ops.repositories.central.MavenCentralRepositoryPublisher
import dev.zucca_ops.repositories.local.LocalRepositoryPublisher
import dev.zucca_ops.repositories.remote.RemoteRepositoryPublisher
import org.gradle.api.Project

object RepositoryPublisherFactory {
    fun get(project: Project, configuration: PluginConfiguration, versionResolver: VersionResolver): RepositoryPublisher {
        val repositoryConfig: RepositoryConfig = if (versionResolver.isRelease()) configuration.prod else configuration.dev

        val repositoryAuthenticator = RepositoryAuthenticator(project, configuration)

        return when(repositoryConfig.target) {
            RepositoryConstants.LOCAL_TARGET_COMMAND -> LocalRepositoryPublisher(project, versionResolver)
            RepositoryConstants.MAVEN_CENTRAL_URL -> MavenCentralRepositoryPublisher(project, versionResolver, repositoryConfig.customGradleCommand!!)
            else -> RemoteRepositoryPublisher(project, versionResolver, repositoryAuthenticator, configuration)
        }
    }
}