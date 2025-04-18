package repositories.central

import dev.zuccaops.configuration.PluginConfiguration
import dev.zuccaops.helpers.VersionResolver
import dev.zuccaops.repositories.RepositoryAuthenticator
import dev.zuccaops.repositories.central.MavenCentralRepositoryPublisher
import io.mockk.*
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactRepositoryContainer.MAVEN_CENTRAL_URL
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testutil.TestProjectFactory

class MavenCentralRepositoryPublisherTest {

    private lateinit var mockProject: Project
    private lateinit var mockVersionResolver: VersionResolver
    private lateinit var mockAuthenticator: RepositoryAuthenticator

    @BeforeEach
    fun setUp() {
        mockProject = TestProjectFactory.create()
        mockVersionResolver = mockk(relaxed = true)
        mockAuthenticator = mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `shouldSign should always return true`() {
        val publisher = MavenCentralRepositoryPublisher(mockProject, mockVersionResolver, mockAuthenticator)
        assertTrue(publisher.shouldSign())
    }

    @Test
    fun `isPublishable should return true if not a release`() {
        every { mockVersionResolver.isRelease() } returns false
        val publisher = MavenCentralRepositoryPublisher(mockProject, mockVersionResolver, mockAuthenticator)
        assertTrue(publisher.isPublishable())
    }

    @Test
    fun `getUri should construct the correct string for the project`() {
        val resolver = mockk<VersionResolver> {
            every { isRelease() } returns true
            every { getVersion() } returns "1.0.0"
        }

        val authenticator = mockk<RepositoryAuthenticator>(relaxed = true)

        val publisher = MavenCentralRepositoryPublisher(mockProject, resolver, authenticator)

        val expectedUrl = MAVEN_CENTRAL_URL + "dev/zucca-ops/my-plugin/1.0.0"
        Assertions.assertEquals(expectedUrl, publisher.getUri())
    }
}