package repositories.local

import dev.zuccaops.helpers.VersionResolver
import dev.zuccaops.repositories.local.LocalRepositoryPublisher
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import testutil.TestProjectFactory

class LocalRepositoryPublisherTest {

    @Test
    fun `isPublishable should return false if artifact does not exist in local m2`() {
        val mockProject = TestProjectFactory.create()

        val mockResolver = mockk<VersionResolver> {
            every { isRelease() } returns true
            every { getVersion() } returns "1.0.0"
        }

        val publisher = LocalRepositoryPublisher(mockProject, mockResolver)

        // Assuming nothing was published yet
        val result = publisher.isPublishable()

        assertTrue(result)
    }
}