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

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.readLines
import kotlin.io.path.writeText

internal class FunctionalTestProject(
    rootDirectory: Path,
    private val branch: String,
    buildScript: String,
    private val gradleVersion: String? = null,
) {
    val projectDirectory: Path = rootDirectory.resolve("project")
    val gradleUserHome: Path = rootDirectory.resolve("gradle-user-home")
    val testKitDirectory: Path = rootDirectory.resolve("test-kit")
    val mavenRepository: Path = rootDirectory.resolve("maven-repository")

    init {
        projectDirectory.createDirectories()
        gradleUserHome.createDirectories()
        testKitDirectory.createDirectories()
        mavenRepository.createDirectories()

        projectDirectory.resolve("settings.gradle.kts").writeText(
            """
            rootProject.name = "test-library"
            """.trimIndent(),
        )
        projectDirectory.resolve("gradle.properties").writeText(
            """
            org.gradle.daemon=false
            kotlin.compiler.execution.strategy=in-process
            """.trimIndent(),
        )
        projectDirectory.resolve("build.gradle.kts").writeText(buildScript)

        val javaSource = projectDirectory.resolve("src/main/java/com/example/Library.java")
        javaSource.parent.createDirectories()
        javaSource.writeText(
            """
            package com.example;

            public final class Library {
                public String value() {
                    return "functional-test";
                }
            }
            """.trimIndent(),
        )

        initializeGitRepository()
    }

    fun build(
        vararg tasks: String,
        arguments: List<String> = emptyList(),
    ): BuildResult = runner(tasks.toList() + arguments).build()

    fun buildAndFail(
        vararg tasks: String,
        arguments: List<String> = emptyList(),
    ): BuildResult = runner(tasks.toList() + arguments).buildAndFail()

    fun versions(): Map<String, String> =
        projectDirectory
            .resolve("build/publisher-versions.txt")
            .readLines()
            .associate { line ->
                val (key, value) = line.split("=", limit = 2)
                key to value
            }

    fun artifactDirectory(version: String): Path = mavenRepository.resolve("com/example/test-library/$version")

    private fun runner(arguments: List<String>): GradleRunner {
        val runner =
            GradleRunner
                .create()
                .withProjectDir(projectDirectory.toFile())
                .withArguments(
                    listOf(
                        "--console=plain",
                        "--stacktrace",
                        "-Dmaven.repo.local=${mavenRepository.toAbsolutePath()}",
                    ) + arguments,
                ).withPluginClasspath()
                .withTestKitDir(testKitDirectory.toFile())
                .withEnvironment(isolatedEnvironment())

        return gradleVersion?.let(runner::withGradleVersion) ?: runner
    }

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

    private fun initializeGitRepository() {
        git("init")
        git("config", "user.name", "Gradle Publisher Functional Tests")
        git("config", "user.email", "functional-tests@example.invalid")
        git("config", "commit.gpgsign", "false")
        git("checkout", "-b", branch)
        git("add", "--all")
        git("commit", "-m", "Initialize functional test project")
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

internal fun publisherBuildScript(
    alterProjectVersion: Boolean = true,
    shadowJar: Boolean = false,
    devConfiguration: String,
    prodConfiguration: String,
    allowInsecureProtocol: Boolean = false,
    extraBuildScript: String = "",
): String {
    val insecureRepositoryConfiguration =
        if (allowInsecureProtocol) {
            """
            publishing.repositories
                .withType<org.gradle.api.artifacts.repositories.MavenArtifactRepository>()
                .configureEach {
                    isAllowInsecureProtocol = true
                }
            """.trimIndent()
        } else {
            ""
        }

    return """
        plugins {
            `java-library`
            id("dev.zucca-ops.gradle-publisher")
        }

        group = "com.example"
        version = "1.2.3"

        publisher {
            alterProjectVersion = $alterProjectVersion
            shadowJar = $shadowJar
            releaseBranchPatterns = listOf("^release/.+${'$'}")

            dev {
        ${devConfiguration.trimIndent().prependIndent("        ")}
            }

            prod {
        ${prodConfiguration.trimIndent().prependIndent("        ")}
            }
        }

        $insecureRepositoryConfiguration

        tasks.register("writePublisherVersions") {
            val versionFile = layout.buildDirectory.file("publisher-versions.txt")
            outputs.file(versionFile)

            doLast {
                val configuration =
                    project.extensions.getByType<dev.zuccaops.configuration.PluginConfiguration>()
                versionFile.get().asFile.writeText(
                    listOf(
                        "resolved=${'$'}{configuration.resolvedVersion}",
                        "effective=${'$'}{configuration.effectiveVersion}",
                        "project=${'$'}{project.version}",
                    ).joinToString(separator = "\n", postfix = "\n"),
                )
            }
        }

        ${extraBuildScript.trimIndent()}
        """.trimIndent()
}

internal fun newFunctionalTestDirectory(name: String): Path =
    Path
        .of(
            System.getProperty("user.dir"),
            "build",
            "functional-test-projects",
            "$name-${UUID.randomUUID()}",
        ).createDirectories()
