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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.Base64

class RemotePublishingFunctionalTest {
    lateinit var temporaryDirectory: Path

    @BeforeEach
    fun createTemporaryDirectory() {
        temporaryDirectory = newFunctionalTestDirectory("remote")
    }

    @Test
    fun `remote release publication proceeds after 404 and sends configured basic authentication`() {
        FakeMavenRepository(existenceStatus = 404).use { repository ->
            val project = remoteReleaseProject(repository)

            val result =
                project.build(
                    "publish",
                    arguments =
                        listOf(
                            "-PrepositoryUser=publisher-user",
                            "-PrepositoryPassword=publisher-password",
                        ),
                )
            val basePath = "com/example/test-library/1.2.3/test-library-1.2.3"
            val expectedAuthorization =
                "Basic " +
                    Base64
                        .getEncoder()
                        .encodeToString("publisher-user:publisher-password".toByteArray())

            assertEquals(TaskOutcome.SUCCESS, result.task(":publishMavenPublicationToMavenRepository")?.outcome)
            assertNotNull(repository.uploadedFile("$basePath.jar"))
            val pom = repository.uploadedFile("$basePath.pom")
            assertNotNull(pom)
            assertTrue(pom!!.toString(Charsets.UTF_8).contains("<version>1.2.3</version>"))

            val existenceRequest =
                repository.requests.single {
                    it.method == "GET" && it.path == "com/example/test-library/1.2.3"
                }
            assertEquals(expectedAuthorization, existenceRequest.authorization)
        }
    }

    @Test
    fun `remote release publication is skipped after 200`() {
        FakeMavenRepository(existenceStatus = 200).use { repository ->
            val project = remoteReleaseProject(repository)

            val result =
                project.build(
                    "publish",
                    arguments =
                        listOf(
                            "-PrepositoryUser=publisher-user",
                            "-PrepositoryPassword=publisher-password",
                        ),
                )

            assertEquals(TaskOutcome.SKIPPED, result.task(":publishMavenPublicationToMavenRepository")?.outcome)
            assertTrue(
                repository.requests.any {
                    it.method == "GET" && it.path == "com/example/test-library/1.2.3"
                },
            )
            assertFalse(repository.requests.any { it.method == "PUT" })
        }
    }

    private fun remoteReleaseProject(repository: FakeMavenRepository): FunctionalTestProject =
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
                        target = "${repository.uri}"
                        usernameProperty = "repositoryUser"
                        passwordProperty = "repositoryPassword"
                        sign = false
                        """,
                    allowInsecureProtocol = true,
                ),
        )
}
