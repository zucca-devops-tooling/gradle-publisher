package repositories.remote

import dev.zuccaops.configuration.PluginConfiguration
import dev.zuccaops.helpers.VersionResolver
import dev.zuccaops.repositories.RepositoryAuthenticator
import dev.zuccaops.repositories.remote.RemoteRepositoryPublisher
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.gradle.api.Project
import org.gradle.internal.impldep.org.junit.Assert.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import testutil.TestProjectFactory

class RemoteRepositoryPublisherTest {

    val project = TestProjectFactory.create()

    @AfterEach
    fun cleanUp() {
        unmockkAll()
    }

    @Test
    fun `isPublishable should return true for dev environment`() {
        val versionResolver = mockk<VersionResolver> {
            every { isRelease() } returns false
        }
        val config = mockk<PluginConfiguration> {
            every { dev } returns mockk { every { sign } returns false }
        }
        val authenticator = mockk<RepositoryAuthenticator>(relaxed = true)

        val publisher = RemoteRepositoryPublisher(project, versionResolver, authenticator, config)

        assertTrue(publisher.isPublishable())
    }

    @Test
    fun `getUri should construct the correct string for the project`() {
        val resolver = mockk<VersionResolver> {
            every { isRelease() } returns true
            every { getVersion() } returns "1.0.0"
        }
        val config = mockk<PluginConfiguration> {
            every { prod } returns mockk { every { target } returns "https://fake.repo/" }
        }
        val authenticator = mockk<RepositoryAuthenticator>(relaxed = true)

        val publisher = RemoteRepositoryPublisher(project, resolver, authenticator, config)

        val expectedUrl = "https://fake.repo/dev/zucca-ops/my-plugin/1.0.0"
        assertEquals(expectedUrl, publisher.getUri())
    }

    @Test
    fun `shouldSign should return true for prod if enabled`() {
        val versionResolver = mockk<VersionResolver> {
            every { isRelease() } returns true
        }
        val config = mockk<PluginConfiguration> {
            every { prod } returns mockk { every { sign } returns true }
            every { dev } returns mockk { every { sign } returns false }
        }
        val authenticator = mockk<RepositoryAuthenticator>()

        val publisher = RemoteRepositoryPublisher(project, versionResolver, authenticator, config)

        assertTrue(publisher.shouldSign())
    }
}