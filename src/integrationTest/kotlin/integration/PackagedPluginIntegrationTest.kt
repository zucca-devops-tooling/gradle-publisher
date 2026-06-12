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
package integration

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.UUID
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText

class PackagedPluginIntegrationTest {
    lateinit var temporaryDirectory: Path

    @BeforeEach
    fun createTemporaryDirectory() {
        temporaryDirectory =
            Path
                .of(System.getProperty("integrationTest.consumerDirectory"))
                .resolve(UUID.randomUUID().toString())
                .createDirectories()
    }

    @Test
    fun `candidate repository contains a resolvable plugin marker implementation and runtime metadata`() {
        val markerPom = markerArtifactDirectory().resolve("$PLUGIN_ID.gradle.plugin-$candidateVersion.pom")
        val implementationPom = implementationArtifactDirectory().resolve("$IMPLEMENTATION_ARTIFACT-$candidateVersion.pom")
        val implementationModule = implementationArtifactDirectory().resolve("$IMPLEMENTATION_ARTIFACT-$candidateVersion.module")
        val implementationJar = implementationArtifactDirectory().resolve("$IMPLEMENTATION_ARTIFACT-$candidateVersion.jar")

        assertTrue(markerPom.exists(), "The Gradle plugin marker POM must be published")
        assertEquals(
            listOf(Coordinate(IMPLEMENTATION_GROUP, IMPLEMENTATION_ARTIFACT, candidateVersion)),
            dependenciesIn(markerPom),
        )

        assertTrue(implementationPom.exists(), "The implementation POM must be published")
        assertTrue(implementationModule.exists(), "Gradle module metadata must be published")
        assertTrue(implementationJar.exists(), "The implementation JAR must be published")
        assertTrue(
            dependenciesIn(implementationPom).contains(
                Coordinate(
                    "tech.yanand.maven-central-publish",
                    "tech.yanand.maven-central-publish.gradle.plugin",
                    "1.2.0",
                ),
            ),
            "The packaged implementation metadata must retain its runtime dependency",
        )

        ZipFile(implementationJar.toFile()).use { jar ->
            val descriptor = jar.getEntry("META-INF/gradle-plugins/$PLUGIN_ID.properties")
            assertNotNull(descriptor, "The implementation JAR must contain the plugin descriptor")
            val properties = Properties()
            jar.getInputStream(descriptor).use(properties::load)
            assertEquals("dev.zuccaops.GradlePublisherPlugin", properties.getProperty("implementation-class"))
        }

        assertTrue(
            candidateRepository
                .resolve(
                    "tech/yanand/maven-central-publish/" +
                        "tech.yanand.maven-central-publish.gradle.plugin/1.2.0/" +
                        "tech.yanand.maven-central-publish.gradle.plugin-1.2.0.pom",
                ).exists(),
        )
        assertTrue(
            candidateRepository
                .resolve(
                    "tech/yanand/gradle/maven-central-publish/1.2.0/" +
                        "maven-central-publish-1.2.0.jar",
                ).exists(),
        )
    }

    @Test
    fun `feature branch resolves and applies the packaged plugin and exposes snapshot versions`() {
        val project = consumerProject("feature-version", "feature/layer-three")

        val result = project.build("writePublisherVersions")

        assertEquals(TaskOutcome.SUCCESS, result.task(":writePublisherVersions")?.outcome)
        assertTrue(result.output.contains("Applying Gradle Publisher Plugin"))
        assertEquals(
            mapOf(
                "resolved" to "2.3.4-feature-layer-three-SNAPSHOT",
                "effective" to "2.3.4-feature-layer-three-SNAPSHOT",
            ),
            project.versions(),
        )
    }

    @Test
    fun `release branch preserves the consumer base version`() {
        val project = consumerProject("release-version", "release/2.3.4")

        val result = project.build("writePublisherVersions")

        assertEquals(TaskOutcome.SUCCESS, result.task(":writePublisherVersions")?.outcome)
        assertEquals(
            mapOf(
                "resolved" to "2.3.4",
                "effective" to "2.3.4",
            ),
            project.versions(),
        )
    }

    @Test
    fun `packaged plugin publishes representative consumer coordinates to a file repository`() {
        val project = consumerProject("publication", "feature/publication")
        val publishedVersion = "2.3.4-feature-publication-SNAPSHOT"

        val result = project.build("publish")

        assertEquals(TaskOutcome.SUCCESS, result.task(":publishMavenPublicationToMavenRepository")?.outcome)
        val artifactDirectory =
            project.publicationRepository.resolve(
                "com/example/integration-consumer/$publishedVersion",
            )
        val publishedJar =
            Files
                .list(artifactDirectory)
                .use { files -> files.filter { it.fileName.toString().endsWith(".jar") }.toList().single() }
        val publishedPom =
            Files
                .list(artifactDirectory)
                .use { files -> files.filter { it.fileName.toString().endsWith(".pom") }.toList().single() }

        assertTrue(publishedJar.exists())
        assertTrue(publishedPom.exists())
        assertEquals(
            Coordinate("com.example", "integration-consumer", publishedVersion),
            pomCoordinate(publishedPom),
        )
    }

    @Test
    fun `plugin resolution fails when the marker is unavailable`() {
        val repositoryWithoutMarker = temporaryDirectory.resolve("repository-without-marker")
        copyCandidateRepositoryWithoutMarker(repositoryWithoutMarker)
        val project =
            consumerProject(
                name = "missing-marker",
                branch = "feature/missing-marker",
                pluginRepository = repositoryWithoutMarker,
            )

        val result = project.buildAndFail("help")

        assertTrue(result.output.contains("Plugin [id: '$PLUGIN_ID', version: '$candidateVersion'] was not found"))
    }

    private fun consumerProject(
        name: String,
        branch: String,
        pluginRepository: Path = candidateRepository,
    ): IntegrationTestProject =
        IntegrationTestProject(
            rootDirectory = temporaryDirectory.resolve(name),
            branch = branch,
            pluginRepository = pluginRepository,
            candidateVersion = candidateVersion,
        )

    private fun markerArtifactDirectory(): Path =
        candidateRepository.resolve(
            "dev/zucca-ops/gradle-publisher/$PLUGIN_ID.gradle.plugin/$candidateVersion",
        )

    private fun implementationArtifactDirectory(): Path =
        candidateRepository.resolve(
            "dev/zucca-ops/gradle-publisher/$candidateVersion",
        )

    private fun copyCandidateRepositoryWithoutMarker(destination: Path) {
        val markerRoot =
            Path.of(
                "dev",
                "zucca-ops",
                "gradle-publisher",
                "$PLUGIN_ID.gradle.plugin",
            )

        Files.walk(candidateRepository).use { paths ->
            paths.forEach { source ->
                val relative = candidateRepository.relativize(source)
                if (!relative.startsWith(markerRoot)) {
                    val target = destination.resolve(relative)
                    if (source.isDirectory()) {
                        target.createDirectories()
                    } else {
                        target.parent.createDirectories()
                        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }
        }
    }

    private fun pomCoordinate(pom: Path): Coordinate {
        val project = parsePom(pom).documentElement
        return Coordinate(
            project.childText("groupId"),
            project.childText("artifactId"),
            project.childText("version"),
        )
    }

    private fun dependenciesIn(pom: Path): List<Coordinate> {
        val dependencies = parsePom(pom).getElementsByTagName("dependency")
        return (0 until dependencies.length).map { index ->
            val dependency = dependencies.item(index) as Element
            Coordinate(
                dependency.childText("groupId"),
                dependency.childText("artifactId"),
                dependency.childText("version"),
            )
        }
    }

    private fun parsePom(pom: Path) =
        DocumentBuilderFactory
            .newInstance()
            .apply {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                isExpandEntityReferences = false
            }.newDocumentBuilder()
            .parse(pom.toFile())

    private fun Element.childText(name: String): String = getElementsByTagName(name).item(0).textContent.trim()

    private val candidateRepository: Path
        get() = Path.of(System.getProperty("integrationTest.repository"))

    private val candidateVersion: String
        get() = System.getProperty("integrationTest.candidateVersion")

    private data class Coordinate(
        val group: String,
        val artifact: String,
        val version: String,
    )

    private companion object {
        const val PLUGIN_ID = "dev.zucca-ops.gradle-publisher"
        const val IMPLEMENTATION_GROUP = "dev.zucca-ops"
        const val IMPLEMENTATION_ARTIFACT = "gradle-publisher"
    }
}

private class IntegrationTestProject(
    rootDirectory: Path,
    branch: String,
    pluginRepository: Path,
    candidateVersion: String,
) {
    val projectDirectory: Path = rootDirectory.resolve("project")
    val publicationRepository: Path = rootDirectory.resolve("publication-repository")
    private val gradleUserHome: Path = rootDirectory.resolve("gradle-user-home")
    private val testKitDirectory: Path = rootDirectory.resolve("test-kit")
    private val mavenLocalRepository: Path = rootDirectory.resolve("maven-local")

    init {
        projectDirectory.createDirectories()
        publicationRepository.createDirectories()
        gradleUserHome.createDirectories()
        testKitDirectory.createDirectories()
        mavenLocalRepository.createDirectories()

        projectDirectory.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    maven {
                        url = uri("${pluginRepository.toUri()}")
                        metadataSources {
                            gradleMetadata()
                            mavenPom()
                            artifact()
                        }
                    }
                }
            }

            rootProject.name = "integration-consumer"
            """.trimIndent(),
        )
        projectDirectory.resolve("gradle.properties").writeText(
            """
            org.gradle.daemon=false
            kotlin.compiler.execution.strategy=in-process
            """.trimIndent(),
        )
        projectDirectory.resolve("build.gradle.kts").writeText(
            """
            plugins {
                `java-library`
                id("dev.zucca-ops.gradle-publisher") version "$candidateVersion"
            }

            group = "com.example"
            version = "2.3.4"

            publisher {
                alterProjectVersion = true
                releaseBranchPatterns = listOf("^release/.+${'$'}")

                dev {
                    target = "${publicationRepository.toUri()}"
                    sign = false
                }

                prod {
                    target = "${publicationRepository.toUri()}"
                    sign = false
                }
            }

            tasks.register("writePublisherVersions") {
                val outputFile = layout.buildDirectory.file("publisher-versions.txt")
                outputs.file(outputFile)

                doLast {
                    val configuration =
                        project.extensions.getByType<dev.zuccaops.configuration.PluginConfiguration>()
                    outputFile.get().asFile.writeText(
                        listOf(
                            "resolved=${'$'}{configuration.resolvedVersion}",
                            "effective=${'$'}{configuration.effectiveVersion}",
                        ).joinToString(separator = "\n", postfix = "\n"),
                    )
                }
            }
            """.trimIndent(),
        )

        val javaSource = projectDirectory.resolve("src/main/java/com/example/Library.java")
        javaSource.parent.createDirectories()
        javaSource.writeText(
            """
            package com.example;

            public final class Library {
                public String value() {
                    return "integration-test";
                }
            }
            """.trimIndent(),
        )

        initializeGitRepository(branch)
    }

    fun build(vararg tasks: String): BuildResult = runner(tasks.toList()).build()

    fun buildAndFail(vararg tasks: String): BuildResult = runner(tasks.toList()).buildAndFail()

    fun versions(): Map<String, String> =
        projectDirectory
            .resolve("build/publisher-versions.txt")
            .readLines()
            .associate { line ->
                val (key, value) = line.split("=", limit = 2)
                key to value
            }

    private fun runner(tasks: List<String>): GradleRunner =
        GradleRunner
            .create()
            .withProjectDir(projectDirectory.toFile())
            .withArguments(
                listOf(
                    "--offline",
                    "--console=plain",
                    "--stacktrace",
                    "-Dmaven.repo.local=${mavenLocalRepository.toAbsolutePath()}",
                ) + tasks,
            ).withTestKitDir(testKitDirectory.toFile())
            .withEnvironment(isolatedEnvironment())

    private fun isolatedEnvironment(): Map<String, String> {
        val branchVariables =
            setOf(
                "GITHUB_HEAD_REF",
                "GITHUB_REF",
                "CI_MERGE_REQUEST_SOURCE_BRANCH_NAME",
                "CI_COMMIT_BRANCH",
                "CI_COMMIT_REF_NAME",
                "CI_COMMIT_TAG",
                "BRANCH_NAME",
                "BRANCH_IS_PRIMARY",
                "BITBUCKET_BRANCH",
                "GIT_BRANCH",
                "CI_DEFAULT_BRANCH",
            )
        val environment =
            System
                .getenv()
                .filterKeys { key -> branchVariables.none { it.equals(key, ignoreCase = true) } }
                .toMutableMap()

        environment["GRADLE_USER_HOME"] = gradleUserHome.toAbsolutePath().toString()
        return environment
    }

    private fun initializeGitRepository(branch: String) {
        git("init")
        git("config", "user.name", "Gradle Publisher Integration Tests")
        git("config", "user.email", "integration-tests@example.invalid")
        git("config", "commit.gpgsign", "false")
        git("checkout", "-b", branch)
        git("add", "--all")
        git("commit", "-m", "Initialize integration test consumer")
    }

    private fun git(vararg arguments: String) {
        val command = listOf("git", "-C", projectDirectory.toAbsolutePath().toString()) + arguments
        val process =
            ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()

        check(exitCode == 0) {
            "Git command failed (${command.joinToString(" ")}):\n$output"
        }
    }
}
