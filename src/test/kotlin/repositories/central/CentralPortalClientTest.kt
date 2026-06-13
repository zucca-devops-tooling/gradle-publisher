package repositories.central

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.zuccaops.repositories.central.CentralPortalClient
import dev.zuccaops.repositories.central.CentralPortalClient.DeploymentState
import dev.zuccaops.repositories.central.CentralPortalException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.InetSocketAddress
import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class CentralPortalClientTest {
    lateinit var temporaryDirectory: Path

    @BeforeEach
    fun createTemporaryDirectory() {
        temporaryDirectory =
            Path
                .of(System.getProperty("user.dir"), "build", "unit-test-projects", UUID.randomUUID().toString())
                .createDirectories()
    }

    @Test
    fun `uploads a multipart bundle with bearer authentication and user managed publishing`() {
        FakeCentralPortalServer().use { server ->
            val bundle = temporaryDirectory.resolve("central-bundle.zip")
            bundle.writeText("bundle-content")

            val deploymentId = client(server).upload(bundle, "USER_MANAGED")

            assertEquals("deployment-123", deploymentId)
            val request = server.requests.single()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/publisher/upload", request.path)
            assertEquals("publishingType=USER_MANAGED", request.query)
            assertEquals("Bearer encoded-token", request.authorization)
            assertTrue(request.contentType.startsWith("multipart/form-data; boundary="))

            val boundary = request.contentType.substringAfter("boundary=")
            val body = request.body.toString(UTF_8)
            assertTrue(body.startsWith("--$boundary\r\n"))
            assertTrue(body.contains("Content-Disposition: form-data; name=\"bundle\"; filename=\"central-bundle.zip\""))
            assertTrue(body.contains("Content-Type: application/octet-stream\r\n\r\nbundle-content"))
            assertTrue(body.endsWith("\r\n--$boundary--\r\n"))
        }
    }

    @Test
    fun `reports upload response failures`() {
        FakeCentralPortalServer(uploadResponse = Response(400, "invalid bundle")).use { server ->
            val bundle = temporaryDirectory.resolve("central-bundle.zip")
            bundle.writeText("bundle-content")

            val exception =
                assertThrows<CentralPortalException> {
                    client(server).upload(bundle, "USER_MANAGED")
                }

            assertEquals("Upload failed, status code: [400], response body: invalid bundle", exception.message)
        }
    }

    @Test
    fun `rejects an empty deployment id`() {
        FakeCentralPortalServer(uploadResponse = Response(201, "  ")).use { server ->
            val bundle = temporaryDirectory.resolve("central-bundle.zip")
            bundle.writeText("bundle-content")

            val exception =
                assertThrows<CentralPortalException> {
                    client(server).upload(bundle, "USER_MANAGED")
                }

            assertTrue(exception.message!!.contains("empty deployment ID"))
        }
    }

    @Test
    fun `polls pending and validating deployments until validated`() {
        FakeCentralPortalServer(
            statusResponses =
                listOf(
                    statusResponse("PENDING"),
                    statusResponse("VALIDATING"),
                    statusResponse("VALIDATED"),
                ),
        ).use { server ->
            val status =
                client(server).waitForDeployment(
                    deploymentId = "deployment-123",
                    timeout = Duration.ofSeconds(60),
                    pollInterval = Duration.ZERO,
                )

            assertEquals(DeploymentState.VALIDATED, status.state)
            assertEquals(3, server.requests.size)
            assertTrue(server.requests.all { it.method == "POST" })
            assertTrue(server.requests.all { it.query == "id=deployment-123" })
        }
    }

    @Test
    fun `accepts publishing and published as terminal states`() {
        listOf(DeploymentState.PUBLISHING, DeploymentState.PUBLISHED).forEach { terminalState ->
            FakeCentralPortalServer(
                statusResponses = listOf(statusResponse(terminalState.name)),
            ).use { server ->
                val status =
                    client(server).waitForDeployment(
                        deploymentId = "deployment-123",
                        timeout = Duration.ofSeconds(60),
                        pollInterval = Duration.ZERO,
                    )

                assertEquals(terminalState, status.state)
                assertEquals(1, server.requests.size)
            }
        }
    }

    @Test
    fun `reports failed deployments with portal response details`() {
        val responseBody = """{"deploymentState":"FAILED","errors":{"pom":["missing name"]}}"""
        FakeCentralPortalServer(
            statusResponses = listOf(Response(200, responseBody)),
        ).use { server ->
            val exception =
                assertThrows<CentralPortalException> {
                    client(server).waitForDeployment(
                        deploymentId = "deployment-123",
                        timeout = Duration.ofSeconds(60),
                        pollInterval = Duration.ZERO,
                    )
                }

            val message = exception.message.orEmpty()
            assertTrue(message.contains("Deployment status is failed: [FAILED]"))
            assertTrue(message.contains("missing name"))
        }
    }

    @Test
    fun `times out with the last deployment state`() {
        var now = 0L
        FakeCentralPortalServer(
            statusResponses = listOf(statusResponse("PENDING")),
        ).use { server ->
            val client =
                CentralPortalClient(
                    baseUri = server.uri,
                    authToken = "encoded-token",
                    sleep = { duration -> now += duration.toNanos() },
                    nanoTime = { now },
                )

            val exception =
                assertThrows<CentralPortalException> {
                    client.waitForDeployment(
                        deploymentId = "deployment-123",
                        timeout = Duration.ofSeconds(2),
                        pollInterval = Duration.ofSeconds(1),
                    )
                }

            assertTrue(exception.message!!.contains("Deployment hasn't finished, status is: [PENDING]"))
            assertEquals(2, server.requests.size)
        }
    }

    @Test
    fun `reports status response failures`() {
        FakeCentralPortalServer(
            statusResponses = listOf(Response(503, "temporarily unavailable")),
        ).use { server ->
            val exception =
                assertThrows<CentralPortalException> {
                    client(server).waitForDeployment(
                        deploymentId = "deployment-123",
                        timeout = Duration.ofSeconds(60),
                        pollInterval = Duration.ZERO,
                    )
                }

            assertEquals(
                "Check status failed, status code: [503], response body: temporarily unavailable",
                exception.message,
            )
        }
    }

    @Test
    fun `reports a missing deployment state`() {
        FakeCentralPortalServer(
            statusResponses = listOf(Response(200, """{"deploymentId":"deployment-123"}""")),
        ).use { server ->
            val exception =
                assertThrows<CentralPortalException> {
                    client(server).waitForDeployment(
                        deploymentId = "deployment-123",
                        timeout = Duration.ofSeconds(60),
                        pollInterval = Duration.ZERO,
                    )
                }

            assertTrue(exception.message!!.contains("did not return the 'deploymentState' field"))
        }
    }

    private fun client(server: FakeCentralPortalServer): CentralPortalClient =
        CentralPortalClient(
            baseUri = server.uri,
            authToken = "encoded-token",
            sleep = {},
        )

    private companion object {
        fun statusResponse(state: String) = Response(200, """{"deploymentState":"$state"}""")
    }
}

private data class Response(
    val status: Int,
    val body: String,
)

private class FakeCentralPortalServer(
    private val uploadResponse: Response = Response(201, "deployment-123"),
    statusResponses: List<Response> = listOf(Response(200, """{"deploymentState":"VALIDATED"}""")),
) : AutoCloseable {
    data class Request(
        val method: String,
        val path: String,
        val query: String?,
        val authorization: String?,
        val contentType: String,
        val body: ByteArray,
    )

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val recordedRequests = CopyOnWriteArrayList<Request>()
    private val responses = statusResponses.toList()
    private val statusIndex = AtomicInteger()

    val uri: URI
        get() = URI("http://127.0.0.1:${server.address.port}")

    val requests: List<Request>
        get() = recordedRequests.toList()

    init {
        server.createContext("/api/v1/publisher/upload") { exchange ->
            record(exchange)
            respond(exchange, uploadResponse)
        }
        server.createContext("/api/v1/publisher/status") { exchange ->
            record(exchange)
            val index = statusIndex.getAndIncrement().coerceAtMost(responses.lastIndex)
            respond(exchange, responses[index])
        }
        server.start()
    }

    override fun close() {
        server.stop(0)
    }

    private fun record(exchange: HttpExchange) {
        recordedRequests +=
            Request(
                method = exchange.requestMethod,
                path = exchange.requestURI.path,
                query = exchange.requestURI.rawQuery,
                authorization = exchange.requestHeaders.getFirst("Authorization"),
                contentType = exchange.requestHeaders.getFirst("Content-Type").orEmpty(),
                body = exchange.requestBody.use { it.readBytes() },
            )
    }

    private fun respond(
        exchange: HttpExchange,
        response: Response,
    ) {
        val body = response.body.toByteArray(UTF_8)
        exchange.sendResponseHeaders(response.status, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
        exchange.close()
    }
}
