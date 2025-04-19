package repositories.central

import dev.zuccaops.helpers.VersionResolver
import dev.zuccaops.repositories.central.NexusRepositoryPublisher
import io.mockk.every
import io.mockk.mockk
import org.gradle.api.artifacts.ArtifactRepositoryContainer.MAVEN_CENTRAL_URL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import testutil.TestProjectFactory

class NexusRepositoryPublisherTest {

    @Test
    fun `shouldSign always returns true`() {
        val publisher = NexusRepositoryPublisher(TestProjectFactory.create(), mockk(), "customCommand")
        assertTrue(publisher.shouldSign())
    }

    @Test
    fun `getUri should return correct artifact path`() {
        // given
        val mockProject = TestProjectFactory.create()

        every { mockProject.group } returns "com.example.library"
        every { mockProject.name } returns "my-lib"

        val mockResolver = mockk<VersionResolver> {
            every { getVersion() } returns "2.3.4"
        }

        val publisher = NexusRepositoryPublisher(mockProject, mockResolver, "someTask")

        // when
        val uri = publisher.getUri()

        // then
        assertEquals(
            MAVEN_CENTRAL_URL + "com/example/library/my-lib/2.3.4",
            uri
        )
    }
}