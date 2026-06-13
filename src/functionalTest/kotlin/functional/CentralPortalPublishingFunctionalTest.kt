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

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.nio.file.Path
import java.util.Base64
import java.util.zip.ZipInputStream

class CentralPortalPublishingFunctionalTest {
    lateinit var temporaryDirectory: Path

    @BeforeEach
    fun createTemporaryDirectory() {
        temporaryDirectory = newFunctionalTestDirectory("central-portal")
    }

    @Test
    fun `publishes a Maven bundle to the Central Portal with a local fake server`() {
        FakeCentralPortal().use { portal ->
            val project =
                FunctionalTestProject(
                    temporaryDirectory,
                    branch = "release/1.2.3",
                    buildScript =
                        publisherBuildScript(
                            devConfiguration =
                                """
                                target = "local"
                                sign = false
                                """,
                            prodConfiguration =
                                """
                                target = "mavenCentral"
                                usernameProperty = "portalUser"
                                passwordProperty = "portalPassword"
                                sign = false
                                """,
                        ),
                )

            val result =
                project.build(
                    "publish",
                    arguments =
                        listOf(
                            "-PportalUser=publisher-user",
                            "-PportalPassword=publisher-password",
                            "-PmavenCentralRepositoryBaseUrl=${portal.repositoryUri}",
                            "-PmavenCentralPortalBaseUrl=${portal.uri}",
                            "-PmavenCentralPortalPollIntervalMillis=1",
                        ),
                )

            assertEquals(TaskOutcome.SUCCESS, result.task(":publishMavenPublicationToLocalRepository")?.outcome)
            assertEquals(TaskOutcome.SUCCESS, result.task(":zipBundleForUpload")?.outcome)
            assertEquals(TaskOutcome.SUCCESS, result.task(":publishToMavenCentralPortal")?.outcome)
            assertFalse(result.output.contains("https://central.sonatype.com/api/"))

            val expectedAuthorization =
                "Bearer " +
                    Base64
                        .getEncoder()
                        .encodeToString("publisher-user:publisher-password".toByteArray())
            val uploadRequest = portal.requests.single { it.path == "/api/v1/publisher/upload" }
            assertEquals("POST", uploadRequest.method)
            assertEquals("publishingType=USER_MANAGED", uploadRequest.query)
            assertEquals(expectedAuthorization, uploadRequest.authorization)

            val bundle = requireNotNull(portal.uploadedBundle)
            val entries = zipEntries(bundle)
            val artifactPath = "com/example/test-library/1.2.3/test-library-1.2.3"
            assertTrue(entries.contains("$artifactPath.jar"))
            assertTrue(entries.contains("$artifactPath.pom"))

            val statusRequests = portal.requests.filter { it.path == "/api/v1/publisher/status" }
            assertEquals(3, statusRequests.size)
            assertTrue(statusRequests.all { it.method == "POST" })
            assertTrue(statusRequests.all { it.query == "id=deployment-123" })
            assertTrue(
                portal.requests.any {
                    it.method == "GET" &&
                        it.path == "/repository/com/example/test-library/1.2.3"
                },
            )
        }
    }

    private fun zipEntries(bundle: ByteArray): Set<String> =
        ZipInputStream(ByteArrayInputStream(bundle)).use { zip ->
            buildSet {
                var entry = zip.nextEntry
                while (entry != null) {
                    add(entry.name)
                    entry = zip.nextEntry
                }
            }
        }
}
