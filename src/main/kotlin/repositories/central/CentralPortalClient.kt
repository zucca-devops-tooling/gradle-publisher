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
package dev.zuccaops.repositories.central

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import java.time.Duration
import java.util.UUID

internal class CentralPortalClient(
    private val baseUri: URI,
    private val authToken: String,
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
    private val sleep: (Duration) -> Unit = { duration -> Thread.sleep(duration.toMillis()) },
    private val nanoTime: () -> Long = System::nanoTime,
) {
    fun uploadAndWait(
        bundle: Path,
        publishingType: String,
        timeout: Duration,
        pollInterval: Duration,
    ): DeploymentStatus {
        val deploymentId = upload(bundle, publishingType)
        return waitForDeployment(deploymentId, timeout, pollInterval)
    }

    internal fun upload(
        bundle: Path,
        publishingType: String,
    ): String {
        val boundary = UUID.randomUUID().toString().replace("-", "")
        val request =
            HttpRequest
                .newBuilder(endpoint("api/v1/publisher/upload?publishingType=${publishingType.urlEncode()}"))
                .header("Authorization", "Bearer $authToken")
                .header("Content-Type", "multipart/form-data; boundary=$boundary")
                .POST(multipartBody(boundary, bundle))
                .build()
        val response = send(request)

        if (response.statusCode() != UPLOAD_SUCCESS_STATUS) {
            throw CentralPortalException(
                "Upload failed, status code: [${response.statusCode()}], response body: ${response.body()}",
            )
        }

        return response.body().trim().ifEmpty {
            throw CentralPortalException("Upload succeeded, but the Central Portal returned an empty deployment ID.")
        }
    }

    internal fun waitForDeployment(
        deploymentId: String,
        timeout: Duration,
        pollInterval: Duration,
    ): DeploymentStatus {
        val deadline = nanoTime() + timeout.toNanos()

        while (true) {
            pause(pollInterval)
            val status = parseDeploymentStatus(deploymentStatus(deploymentId))

            when (status.state) {
                DeploymentState.VALIDATED,
                DeploymentState.PUBLISHING,
                DeploymentState.PUBLISHED,
                -> {
                    return status
                }

                DeploymentState.FAILED -> {
                    throw CentralPortalException(
                        "Deployment status is failed: [${status.state}], response body: ${status.responseBody}, " +
                            "please go to [$DEPLOYMENTS_URL] check your deployment.",
                    )
                }

                DeploymentState.PENDING,
                DeploymentState.VALIDATING,
                -> {
                    Unit
                }
            }

            if (nanoTime() >= deadline) {
                throw CentralPortalException(
                    "Deployment hasn't finished, status is: [${status.state}], " +
                        "please go to [$DEPLOYMENTS_URL] check your deployment.",
                )
            }
        }
    }

    private fun pause(duration: Duration) {
        try {
            sleep(duration)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw CentralPortalException("Central Portal status polling was interrupted.", exception)
        }
    }

    private fun deploymentStatus(deploymentId: String): String {
        val request =
            HttpRequest
                .newBuilder(endpoint("api/v1/publisher/status?id=${deploymentId.urlEncode()}"))
                .header("Authorization", "Bearer $authToken")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build()
        val response = send(request)

        if (response.statusCode() != STATUS_SUCCESS_STATUS) {
            throw CentralPortalException(
                "Check status failed, status code: [${response.statusCode()}], response body: ${response.body()}",
            )
        }

        return response.body()
    }

    private fun parseDeploymentStatus(responseBody: String): DeploymentStatus {
        val stateValue =
            DEPLOYMENT_STATE_PATTERN
                .find(responseBody)
                ?.groupValues
                ?.get(1)
                ?: throw CentralPortalException(
                    "The API did not return the 'deploymentState' field. Response body: $responseBody",
                )
        val state =
            runCatching { DeploymentState.valueOf(stateValue) }.getOrElse {
                throw CentralPortalException(
                    "The API returned an unsupported deployment state: [$stateValue]. Response body: $responseBody",
                )
            }

        return DeploymentStatus(state, responseBody)
    }

    private fun send(request: HttpRequest): HttpResponse<String> =
        try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString(UTF_8))
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw CentralPortalException("Central Portal request was interrupted.", exception)
        } catch (exception: Exception) {
            throw CentralPortalException("Central Portal request failed: ${exception.message}", exception)
        }

    private fun endpoint(relativePath: String): URI {
        val normalizedBase = baseUri.toString().trimEnd('/')
        return URI.create("$normalizedBase/$relativePath")
    }

    private fun multipartBody(
        boundary: String,
        bundle: Path,
    ): HttpRequest.BodyPublisher {
        val prefix =
            buildString {
                append("--")
                append(boundary)
                append(CRLF)
                append("Content-Disposition: form-data; name=\"bundle\"; filename=\"")
                append(bundle.fileName)
                append("\"")
                append(CRLF)
                append("Content-Type: application/octet-stream")
                append(CRLF)
                append(CRLF)
            }
        val suffix = "$CRLF--$boundary--$CRLF"

        return HttpRequest.BodyPublishers.concat(
            HttpRequest.BodyPublishers.ofString(prefix, UTF_8),
            HttpRequest.BodyPublishers.ofFile(bundle),
            HttpRequest.BodyPublishers.ofString(suffix, UTF_8),
        )
    }

    private fun String.urlEncode(): String = URLEncoder.encode(this, UTF_8)

    internal data class DeploymentStatus(
        val state: DeploymentState,
        val responseBody: String,
    )

    internal enum class DeploymentState {
        PENDING,
        VALIDATING,
        VALIDATED,
        PUBLISHING,
        PUBLISHED,
        FAILED,
    }

    private companion object {
        const val CRLF = "\r\n"
        const val UPLOAD_SUCCESS_STATUS = 201
        const val STATUS_SUCCESS_STATUS = 200
        const val DEPLOYMENTS_URL = "https://central.sonatype.com/publishing/deployments"
        val DEPLOYMENT_STATE_PATTERN = Regex(""""deploymentState"\s*:\s*"([^"]+)"""")
    }
}

internal class CentralPortalException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
