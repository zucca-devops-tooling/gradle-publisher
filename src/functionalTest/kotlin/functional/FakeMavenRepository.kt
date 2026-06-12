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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

internal class FakeMavenRepository(
    private val existenceStatus: Int,
) : AutoCloseable {
    data class Request(
        val method: String,
        val path: String,
        val authorization: String?,
    )

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val uploadedFiles = ConcurrentHashMap<String, ByteArray>()
    private val recordedRequests = CopyOnWriteArrayList<Request>()

    val uri: URI
        get() = URI("http://127.0.0.1:${server.address.port}/repository")

    val requests: List<Request>
        get() = recordedRequests.toList()

    init {
        server.createContext("/repository") { exchange -> handle(exchange) }
        server.start()
    }

    fun uploadedFile(path: String): ByteArray? = uploadedFiles[path]

    override fun close() {
        server.stop(0)
    }

    private fun handle(exchange: HttpExchange) {
        val path = exchange.requestURI.path.removePrefix("/repository/")
        val requestBody = exchange.requestBody.use { it.readBytes() }

        recordedRequests +=
            Request(
                method = exchange.requestMethod,
                path = path,
                authorization = exchange.requestHeaders.getFirst("Authorization"),
            )

        when (exchange.requestMethod) {
            "GET", "HEAD" -> {
                respondToExistenceCheck(exchange)
            }

            "PUT" -> {
                uploadedFiles[path] = requestBody
                exchange.sendResponseHeaders(201, -1)
            }

            else -> {
                exchange.sendResponseHeaders(405, -1)
            }
        }

        exchange.close()
    }

    private fun respondToExistenceCheck(exchange: HttpExchange) {
        if (existenceStatus == 200 && exchange.requestMethod != "HEAD") {
            val body = "exists".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        } else {
            exchange.sendResponseHeaders(existenceStatus, -1)
        }
    }
}
