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

import dev.zuccaops.helpers.VersionResolver
import dev.zuccaops.repositories.BaseRepositoryPublisher
import dev.zuccaops.repositories.RepositoryAuthenticator
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactRepositoryContainer.MAVEN_CENTRAL_URL
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.internal.impldep.com.google.common.annotations.VisibleForTesting
import java.io.FileNotFoundException
import java.net.URL
import java.util.Base64

/**
 * Publisher that targets the new Maven Central (Central Portal) using the [flying-gradle-plugin](https://github.com/yananhub/flying-gradle-plugin).
 *
 * This class dynamically applies the plugin and configures it using reflection to avoid compile-time dependency.
 * It uses `USER_MANAGED` publishing mode, so manual review and publishing is expected in the portal.
 *
 * For release branches, the plugin checks if the artifact already exists in Maven Central
 * and skips publishing if it has already been released.
 *
 * Artifacts are always signed in this mode.
 *
 * @param project The Gradle project
 * @param versionResolver Resolves version and release state
 * @param repositoryAuthenticator Provides credentials for Maven Central
 *
 * @author Guido Zuccarelli
 */
class MavenCentralRepositoryPublisher(
    private val project: Project,
    private val versionResolver: VersionResolver,
    private val repositoryAuthenticator: RepositoryAuthenticator,
) : BaseRepositoryPublisher(project, versionResolver) {
    /**
     * Applies the `flying-gradle-plugin` and configures its required extension using reflection.
     * Also finalizes the `publish` task with `publishToMavenCentralPortal`.
     */
    @Suppress("UNCHECKED_CAST")
    override fun configurePublishingRepository() {
        super.configurePublishingRepository()

        if (repositoryAuthenticator.getProdUsername() != null && repositoryAuthenticator.getProdPassword() != null) {

            project.pluginManager.apply("tech.yanand.maven-central-publish")

            val encodedCredentials =
                encodeBasicAuth(
                    repositoryAuthenticator.getProdUsername()!!,
                    repositoryAuthenticator.getProdPassword()!!,
                )
            project.extensions.configure(
                "mavenCentral",
                Action<Any> {
                    val clazz = this.javaClass

                    val repoDir = project.layout.buildDirectory.dir("repos/bundles")

                    clazz
                        .getMethod("getRepoDir")
                        .invoke(this)
                        .let { it as DirectoryProperty }
                        .set(repoDir)

                    clazz
                        .getMethod("getAuthToken")
                        .invoke(this)
                        .let { it as Property<String> }
                        .set(encodedCredentials)

                    clazz
                        .getMethod("getPublishingType")
                        .invoke(this)
                        .let { it as Property<String> }
                        .set("USER_MANAGED")
                },
            )

            project.tasks.named("publish").configure {
                finalizedBy("publishToMavenCentralPortal")
            }
        } else {
            project.logger.lifecycle("Cannot publish to mavenCentral, credentials not found")
        }
    }

    private fun encodeBasicAuth(
        user: String,
        token: String,
    ): String {
        val authString = "$user:$token"
        return Base64.getEncoder().encodeToString(authString.toByteArray(Charsets.UTF_8))
    }

    @VisibleForTesting
    fun getUri(): String {
        val group = project.group.toString().replace(".", "/")
        val name = project.name.replace(".", "/")

        return MAVEN_CENTRAL_URL + group + "/" + name + "/" + versionResolver.getVersion()
    }

    /**
     * Checks if the artifact already exists in Maven Central to avoid re-uploading.
     */
    private fun artifactAlreadyPublished(): Boolean {
        try {
            URL(getUri()).readBytes()

            return true
        } catch (e: FileNotFoundException) {
            return false
        }
    }

    override fun isPublishable(): Boolean = !versionResolver.isRelease() || !artifactAlreadyPublished()

    /**
     * Always enable signing for Maven Central.
     */
    override fun shouldSign(): Boolean = true

    /**
     * Register a local bundle directory that flying-gradle-plugin will zip and upload.
     */
    override fun registerRepository(repositoryHandler: RepositoryHandler) {
        repositoryHandler.maven {
            name = "Local"
            url =
                project.layout.buildDirectory
                    .dir("repos/bundles")
                    .get()
                    .asFile
                    .toURI()
        }
    }
}
