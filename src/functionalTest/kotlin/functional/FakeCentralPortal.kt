/*
 * Copyright 2025 GuidoZuccarelli
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
package functional

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

internal class FakeCentralPortal : AutoCloseable {
    data class Request(
        val method: String,
        val path: String,
        val query: String?,
        val authorization: String?,
    )

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val recordedRequests = CopyOnWriteArrayList<Request>()
    private val statusIndex = AtomicInteger()
    private val statuses = listOf("PENDING", "VALIDATING", "VALIDATED")

    var uploadedBundle: ByteArray? = null
        private set

    val uri: URI
        get() = URI("http://127.0.0.1:${server.address.port}")

    val repositoryUri: URI
        get() = uri.resolve("/repository")

    val requests: List<Request>
        get() = recordedRequests.toList()

    init {
        server.createContext("/repository") { exchange ->
            record(exchange)
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }
        server.createContext("/api/v1/publisher/upload") { exchange ->
            record(exchange)
            uploadedBundle = extractBundle(exchange)
            respond(exchange, 201, "deployment-123")
        }
        server.createContext("/api/v1/publisher/status") { exchange ->
            record(exchange)
            val index = statusIndex.getAndIncrement().coerceAtMost(statuses.lastIndex)
            respond(exchange, 200, """{"deploymentState":"${statuses[index]}"}""")
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
            )
    }

    private fun extractBundle(exchange: HttpExchange): ByteArray {
        val contentType = exchange.requestHeaders.getFirst("Content-Type")
        val boundary = contentType.substringAfter("boundary=")
        val body = exchange.requestBody.use { it.readBytes() }
        val contentStart = body.indexOf("\r\n\r\n".toByteArray(UTF_8)) + 4
        val contentEnd = body.indexOf("\r\n--$boundary--".toByteArray(UTF_8), contentStart)

        check(contentStart >= 4 && contentEnd >= contentStart) {
            "Invalid multipart request body"
        }
        return body.copyOfRange(contentStart, contentEnd)
    }

    private fun respond(
        exchange: HttpExchange,
        status: Int,
        response: String,
    ) {
        val body = response.toByteArray(UTF_8)
        exchange.sendResponseHeaders(status, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
        exchange.close()
    }
}

private fun ByteArray.indexOf(
    target: ByteArray,
    startIndex: Int = 0,
): Int {
    if (target.isEmpty()) {
        return startIndex.coerceAtMost(size)
    }

    for (index in startIndex..size - target.size) {
        if (target.indices.all { offset -> this[index + offset] == target[offset] }) {
            return index
        }
    }

    return -1
}
