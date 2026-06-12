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
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

class VersionAndLocalPublishingFunctionalTest {
    lateinit var temporaryDirectory: Path

    @BeforeEach
    fun createTemporaryDirectory() {
        temporaryDirectory = newFunctionalTestDirectory("version-local")
    }

    @Test
    fun `feature branch selects dev strategy and publishes an escaped snapshot version locally`() {
        val project =
            FunctionalTestProject(
                temporaryDirectory,
                branch = "feature/use-testkit",
                buildScript =
                    publisherBuildScript(
                        alterProjectVersion = true,
                        devConfiguration = localRepositoryConfiguration,
                        prodConfiguration = unusedNexusConfiguration,
                    ),
            )

        val result = project.build("writePublisherVersions", "publish")
        val version = "1.2.3-feature-use-testkit-SNAPSHOT"
        val artifactDirectory = project.artifactDirectory(version)

        assertEquals(TaskOutcome.SUCCESS, result.task(":writePublisherVersions")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":publishMavenPublicationToMavenLocalRepository")?.outcome)
        assertEquals(
            mapOf(
                "resolved" to version,
                "effective" to version,
                "project" to version,
            ),
            project.versions(),
        )
        val publishedJar = artifactDirectory.toFile().listFiles { file -> file.extension == "jar" }!!.single()
        val publishedPom = artifactDirectory.toFile().listFiles { file -> file.extension == "pom" }!!.single()
        assertTrue(publishedJar.isFile)
        assertTrue(publishedPom.readText().contains("<version>$version</version>"))
    }

    @Test
    fun `configured release branch selects prod strategy and preserves the base version`() {
        val project =
            FunctionalTestProject(
                temporaryDirectory,
                branch = "release/1.2.3",
                buildScript =
                    publisherBuildScript(
                        devConfiguration = unusedNexusConfiguration,
                        prodConfiguration = localRepositoryConfiguration,
                    ),
            )

        val result = project.build("writePublisherVersions", "publish")
        val artifactDirectory = project.artifactDirectory("1.2.3")

        assertEquals(TaskOutcome.SUCCESS, result.task(":publishMavenPublicationToMavenLocalRepository")?.outcome)
        assertEquals(
            mapOf(
                "resolved" to "1.2.3",
                "effective" to "1.2.3",
                "project" to "1.2.3",
            ),
            project.versions(),
        )
        assertTrue(artifactDirectory.resolve("test-library-1.2.3.jar").toFile().isFile)
        assertTrue(artifactDirectory.resolve("test-library-1.2.3.pom").toFile().isFile)
    }

    @Test
    fun `alterProjectVersion false keeps the project version while exposing the resolved version`() {
        val project =
            FunctionalTestProject(
                temporaryDirectory,
                branch = "feature/static-project-version",
                buildScript =
                    publisherBuildScript(
                        alterProjectVersion = false,
                        devConfiguration = localRepositoryConfiguration,
                        prodConfiguration = localRepositoryConfiguration,
                    ),
            )

        val result = project.build("writePublisherVersions")

        assertEquals(TaskOutcome.SUCCESS, result.task(":writePublisherVersions")?.outcome)
        assertEquals(
            mapOf(
                "resolved" to "1.2.3-feature-static-project-version-SNAPSHOT",
                "effective" to "1.2.3",
                "project" to "1.2.3",
            ),
            project.versions(),
        )
    }

    @Test
    fun `local release publication is skipped when the artifact already exists`() {
        val project =
            FunctionalTestProject(
                temporaryDirectory,
                branch = "release/1.2.3",
                buildScript =
                    publisherBuildScript(
                        devConfiguration = localRepositoryConfiguration,
                        prodConfiguration = localRepositoryConfiguration,
                    ),
            )
        val artifactDirectory = project.artifactDirectory("1.2.3")
        val existingJar = artifactDirectory.resolve("test-library-1.2.3.jar")
        artifactDirectory.createDirectories()
        existingJar.writeText("existing artifact")

        val result = project.build("publish")

        assertEquals(TaskOutcome.SKIPPED, result.task(":publishMavenPublicationToMavenLocalRepository")?.outcome)
        assertEquals("existing artifact", existingJar.readText())
        assertFalse(artifactDirectory.resolve("test-library-1.2.3.pom").toFile().exists())
    }

    private companion object {
        val localRepositoryConfiguration =
            """
            target = "local"
            sign = false
            """

        val unusedNexusConfiguration =
            """
            target = "nexus"
            customGradleCommand = "mustNotRun"
            sign = false
            """
    }
}
