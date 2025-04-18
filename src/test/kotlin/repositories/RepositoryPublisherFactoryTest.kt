package repositories

import dev.zuccaops.configuration.PluginConfiguration
import dev.zuccaops.configuration.RepositoryConfig
import dev.zuccaops.helpers.VersionResolver
import dev.zuccaops.repositories.RepositoryConstants
import dev.zuccaops.repositories.RepositoryPublisherFactory
import dev.zuccaops.repositories.central.MavenCentralRepositoryPublisher
import dev.zuccaops.repositories.central.NexusRepositoryPublisher
import dev.zuccaops.repositories.local.LocalRepositoryPublisher
import dev.zuccaops.repositories.remote.RemoteRepositoryPublisher
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import org.gradle.api.Project
import org.gradle.internal.impldep.org.junit.Assert.assertTrue
import org.junit.jupiter.api.Test

class RepositoryPublisherFactoryTest {


    private val project = mockk<Project>(relaxed = true)

    @Test
    fun `should return RemoteRepositoryPublisher in dev mode`() {
        // given
        val devConfig = mockk<RepositoryConfig> {
            every { target } returns "https://example.com/dev"
        }
        val prodConfig = mockk<RepositoryConfig> {
            every { target } returns "https://example.com/prod"
        }

        val config = mockk<PluginConfiguration> {
            every { dev } returns devConfig
            every { prod } returns prodConfig
            every { gitFolder } returns "."
        }

        mockkConstructor(VersionResolver::class)
        every { anyConstructed<VersionResolver>().isRelease() } returns false

        // when
        val publisher = RepositoryPublisherFactory.get(project, config)

        // then
        assertTrue(publisher is RemoteRepositoryPublisher)
    }

    @Test
    fun `should return MavenCentralRepositoryPublisher in prod mode when target is mavenCentral`() {
        // given
        val devConfig = mockk<RepositoryConfig> {
            every { target } returns "dev"
        }
        val prodConfig = mockk<RepositoryConfig> {
            every { target } returns RepositoryConstants.MAVEN_CENTRAL_COMMAND
        }

        val config = mockk<PluginConfiguration> {
            every { dev } returns devConfig
            every { prod } returns prodConfig
            every { gitFolder } returns "."
        }

        mockkConstructor(VersionResolver::class)
        every { anyConstructed<VersionResolver>().isRelease() } returns true

        // when
        val publisher = RepositoryPublisherFactory.get(project, config)

        // then
        assertTrue(publisher is MavenCentralRepositoryPublisher)
    }

    @Test
    fun `should return SonatypeRepositoryPublisher when target is nexus`() {
        // given
        val devConfig = mockk<RepositoryConfig> {
            every { target } returns "dev"
        }
        val prodConfig = mockk<RepositoryConfig> {
            every { target } returns RepositoryConstants.SONATYPE_COMMAND
            every { customGradleCommand } returns "closeAndReleaseStagingRepositories"
        }

        val config = mockk<PluginConfiguration> {
            every { dev } returns devConfig
            every { prod } returns prodConfig
            every { gitFolder } returns "."
        }

        mockkConstructor(VersionResolver::class)
        every { anyConstructed<VersionResolver>().isRelease() } returns true

        // when
        val publisher = RepositoryPublisherFactory.get(project, config)

        // then
        assertTrue(publisher is NexusRepositoryPublisher)
    }



    @Test
    fun `should return SonatypeRepositoryPublisher when target is local`() {
        // given
        val devConfig = mockk<RepositoryConfig> {
            every { target } returns RepositoryConstants.LOCAL_TARGET_COMMAND
        }
        val prodConfig = mockk<RepositoryConfig> {
            every { target } returns RepositoryConstants.SONATYPE_COMMAND
            every { customGradleCommand } returns "closeAndReleaseStagingRepositories"
        }

        val config = mockk<PluginConfiguration> {
            every { dev } returns devConfig
            every { prod } returns prodConfig
            every { gitFolder } returns "."
        }

        mockkConstructor(VersionResolver::class)
        every { anyConstructed<VersionResolver>().isRelease() } returns false

        // when
        val publisher = RepositoryPublisherFactory.get(project, config)

        // then
        assertTrue(publisher is LocalRepositoryPublisher)
    }
}