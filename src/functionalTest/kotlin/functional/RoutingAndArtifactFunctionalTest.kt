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
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.readText
import kotlin.io.path.writeText

class RoutingAndArtifactFunctionalTest {
    lateinit var temporaryDirectory: Path

    @BeforeEach
    fun createTemporaryDirectory() {
        temporaryDirectory = newFunctionalTestDirectory("routing-artifact")
    }

    @Test
    fun `nexus publishing reroutes publish to the configured task`() {
        val project =
            FunctionalTestProject(
                temporaryDirectory,
                branch = "feature/nexus-routing",
                buildScript =
                    publisherBuildScript(
                        devConfiguration =
                            """
                            target = "nexus"
                            customGradleCommand = "dummyNexusPublish"
                            """,
                        prodConfiguration = localRepositoryConfiguration,
                        extraBuildScript =
                            """
                            tasks.register("dummyNexusPublish") {
                                val marker = layout.buildDirectory.file("nexus-routed.txt")
                                outputs.file(marker)

                                doLast {
                                    marker.get().asFile.writeText("routed")
                                }
                            }
                            """,
                    ),
            )

        val result = project.build("publish")

        assertEquals(TaskOutcome.SUCCESS, result.task(":dummyNexusPublish")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":reroutePublishToNexus")?.outcome)
        assertEquals(TaskOutcome.SKIPPED, result.task(":publish")?.outcome)
        assertEquals("routed", project.projectDirectory.resolve("build/nexus-routed.txt").readText())
    }

    @Test
    fun `nexus publishing fails clearly when its task is missing`() {
        val project =
            FunctionalTestProject(
                temporaryDirectory,
                branch = "feature/nexus-missing-task",
                buildScript =
                    publisherBuildScript(
                        devConfiguration =
                            """
                            target = "nexus"
                            """,
                        prodConfiguration = localRepositoryConfiguration,
                    ),
            )

        val result = project.buildAndFail("publish")

        assertTrue(
            result.output.contains(
                "Could not find Gradle task 'closeAndReleaseStagingRepositories'. " +
                    "Please check your configuration or plugin setup.",
            ),
        )
    }

    @Test
    fun `shadowJar true publishes the configured task artifact`() {
        val project =
            FunctionalTestProject(
                temporaryDirectory,
                branch = "feature/shadow-artifact",
                buildScript =
                    publisherBuildScript(
                        shadowJar = true,
                        devConfiguration = localRepositoryConfiguration,
                        prodConfiguration = localRepositoryConfiguration,
                        extraBuildScript =
                            """
                            tasks.register<org.gradle.api.tasks.bundling.Jar>("shadowJar") {
                                archiveClassifier.set("")
                                destinationDirectory.set(layout.buildDirectory.dir("shadow-libs"))
                                from(layout.projectDirectory.file("shadow-only.txt"))
                            }
                            """,
                    ),
            )
        project.projectDirectory.resolve("shadow-only.txt").writeText("shadow artifact")

        val result = project.build("publish")
        val version = "1.2.3-feature-shadow-artifact-SNAPSHOT"
        val publishedJar =
            project
                .artifactDirectory(version)
                .toFile()
                .listFiles { file -> file.extension == "jar" }!!
                .single()

        assertEquals(TaskOutcome.SUCCESS, result.task(":shadowJar")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":publishMavenPublicationToMavenLocalRepository")?.outcome)
        assertTrue(publishedJar.isFile)
        ZipFile(publishedJar).use { jar ->
            assertTrue(jar.getEntry("shadow-only.txt") != null)
            assertFalse(jar.entries().asSequence().any { it.name.endsWith("Library.class") })
        }
    }

    @Test
    fun `shadowJar true fails clearly when the task is absent`() {
        val project =
            FunctionalTestProject(
                temporaryDirectory,
                branch = "feature/shadow-missing-task",
                buildScript =
                    publisherBuildScript(
                        shadowJar = true,
                        devConfiguration = localRepositoryConfiguration,
                        prodConfiguration = localRepositoryConfiguration,
                    ),
            )

        val result = project.buildAndFail("publish")

        assertTrue(result.output.contains("Publisher: 'shadowJar = true', but task 'shadowJar' was not found."))
    }

    private companion object {
        val localRepositoryConfiguration =
            """
            target = "local"
            sign = false
            """
    }
}
