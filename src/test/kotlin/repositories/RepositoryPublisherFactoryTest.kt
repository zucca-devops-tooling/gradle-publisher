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
import io.mockk.*
import org.gradle.internal.impldep.org.junit.Assert.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testutil.TestProjectFactory

class RepositoryPublisherFactoryTest {


    private val project = TestProjectFactory.create()
    val config = mockk<PluginConfiguration>(relaxed = true)


    @BeforeEach
    fun setUp() {
        mockkConstructor(VersionResolver::class)
        every { anyConstructed<VersionResolver>().getVersion() } returns "1.0.0-snapshot"
        every { anyConstructed<VersionResolver>().getVersionForProject() } returns "1.0.0"
        every { config.resolvedVersionInternal = any() } just Runs
        every { config.effectiveVersionInternal = any() } just Runs
    }

    @Test
    fun `should return RemoteRepositoryPublisher in dev mode`() {
        // given
        val devConfig = mockk<RepositoryConfig> {
            every { target } returns "https://example.com/dev"
        }
        val prodConfig = mockk<RepositoryConfig> {
            every { target } returns "https://example.com/prod"
        }

        every { config.dev } returns devConfig
        every { config.prod } returns prodConfig
        every { config.gitFolder } returns "."

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

        every { config.dev } returns devConfig
        every { config.prod } returns prodConfig
        every { config.gitFolder } returns "."

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

        every { config.dev } returns devConfig
        every { config.prod } returns prodConfig
        every { config.gitFolder } returns "."

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

        every { config.dev } returns devConfig
        every { config.prod } returns prodConfig
        every { config.gitFolder } returns "."

        every { anyConstructed<VersionResolver>().isRelease() } returns false

        // when
        val publisher = RepositoryPublisherFactory.get(project, config)

        // then
        assertTrue(publisher is LocalRepositoryPublisher)
    }
}