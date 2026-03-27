package repositories.remote

import com.sun.net.httpserver.HttpServer
import dev.zuccaops.configuration.PluginConfiguration
import dev.zuccaops.helpers.VersionResolver
import dev.zuccaops.helpers.publisherConfiguration
import dev.zuccaops.repositories.RepositoryAuthenticator
import dev.zuccaops.repositories.RepositoryCredentials
import dev.zuccaops.repositories.remote.RemoteRepositoryPublisher
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.gradle.internal.impldep.org.junit.Assert.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import testutil.TestProjectFactory
import java.net.InetSocketAddress
import java.util.Base64

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
        every { project.publisherConfiguration() } returns config
        val authenticator = mockk<RepositoryAuthenticator>(relaxed = true)

        val publisher = RemoteRepositoryPublisher(project, versionResolver, authenticator)

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
        every { project.publisherConfiguration() } returns config

        val authenticator = mockk<RepositoryAuthenticator>(relaxed = true)

        val publisher = RemoteRepositoryPublisher(project, resolver, authenticator)

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

        every { project.publisherConfiguration() } returns config

        val authenticator = mockk<RepositoryAuthenticator>()

        val publisher = RemoteRepositoryPublisher(project, versionResolver, authenticator)

        assertTrue(publisher.shouldSign())
    }

    @Test
    fun `isPublishable should use explicit basic auth for release checks`() {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        val credentials = RepositoryCredentials("publisher-user", "publisher-pass")
        val expectedAuth =
            "Basic " +
                Base64.getEncoder().encodeToString("${credentials.username}:${credentials.password}".toByteArray(Charsets.UTF_8))

        server.createContext("/dev/zucca-ops/my-plugin/1.0.0") { exchange ->
            val authHeader = exchange.requestHeaders.getFirst("Authorization")

            if (authHeader == expectedAuth) {
                val body = "published".toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            } else {
                exchange.sendResponseHeaders(401, -1)
                exchange.close()
            }
        }
        server.start()

        try {
            val versionResolver = mockk<VersionResolver> {
                every { isRelease() } returns true
                every { getVersion() } returns "1.0.0"
            }
            val config = mockk<PluginConfiguration>(relaxed = true) {
                every { prod } returns mockk { every { target } returns "http://127.0.0.1:${server.address.port}" }
            }

            every { project.publisherConfiguration() } returns config

            val authenticator = mockk<RepositoryAuthenticator> {
                every { getProdCredentials() } returns credentials
            }

            val publisher = RemoteRepositoryPublisher(project, versionResolver, authenticator)

            assertFalse(publisher.isPublishable())
        } finally {
            server.stop(0)
        }
    }
}
