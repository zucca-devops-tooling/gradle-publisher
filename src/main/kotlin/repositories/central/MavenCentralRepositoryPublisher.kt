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
import dev.zuccaops.repositories.RepositoryAuthenticator
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactRepositoryContainer.MAVEN_CENTRAL_URL
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.bundling.Zip
import java.util.Base64

/**
 * Publisher that targets the Maven Central Portal.
 *
 * It stages Maven-layout artifacts locally, creates a deployment bundle, and uploads it using
 * `USER_MANAGED` publishing mode so manual review and publishing is expected in the portal.
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
    repositoryBaseUrl: String = MAVEN_CENTRAL_URL.toString(),
) : SonatypeRepositoryPublisher(project, versionResolver, repositoryBaseUrl) {
    companion object {
        const val ROUTING_COMMAND = "publishToMavenCentralPortal"
        const val ZIP_TASK_NAME = "zipBundleForUpload"
        const val DEFAULT_REPO_DIR_PATH = "repos/bundles"
        const val DEFAULT_PORTAL_BASE_URL = "https://central.sonatype.com"
        const val DEFAULT_MAX_WAIT_SECONDS = 60L
        const val DEFAULT_POLL_INTERVAL_MILLIS = 10_000L
        const val PUBLISHING_TYPE_USER_MANAGED = "USER_MANAGED"
        const val PORTAL_BASE_URL_PROPERTY = "mavenCentralPortalBaseUrl"
        const val MAX_WAIT_SECONDS_PROPERTY = "mavenCentralPortalMaxWaitSeconds"
        const val POLL_INTERVAL_MILLIS_PROPERTY = "mavenCentralPortalPollIntervalMillis"
        const val REPOSITORY_BASE_URL_PROPERTY = "mavenCentralRepositoryBaseUrl"
    }

    /**
     * Configures bundle creation and finalizes `publish` with the Central Portal upload.
     */
    override fun configurePublishingRepository() {
        super.configurePublishingRepository()

        if (!isPublishable()) {
            return
        }

        val username = repositoryAuthenticator.getProdUsername()
        val password = repositoryAuthenticator.getProdPassword()

        if (username == null || password == null) {
            if (username == null) {
                project.logger.error("Username needs to be configured for Maven Central publications")
            }
            if (password == null) {
                project.logger.error("Password needs to be configured for Maven Central publications")
            }
            return
        }

        if (!versionResolver.isRelease()) {
            project.logger.error("Maven Central is not allowing snapshots yet, please configure a different target for dev environments")
            return
        }

        registerPortalTasks(encodeBasicAuth(username, password))

        project.tasks.named("publish").configure {
            project.logger.lifecycle("Routing 'publish' to '$ROUTING_COMMAND' after finish")
            finalizedBy(ROUTING_COMMAND)
        }
    }

    private fun encodeBasicAuth(
        user: String,
        token: String,
    ): String {
        project.logger.info("Encoding Maven Central credentials for auth token")
        val authString = "$user:$token"
        return Base64.getEncoder().encodeToString(authString.toByteArray(Charsets.UTF_8))
    }

    /**
     * Always enable signing for Maven Central.
     */
    override fun shouldSign(): Boolean = true

    /**
     * Register the local Maven repository that will be zipped and uploaded.
     */
    override fun registerRepository(repositoryHandler: RepositoryHandler) {
        repositoryHandler.maven {
            name = "Local"
            url =
                project.layout.buildDirectory
                    .dir(DEFAULT_REPO_DIR_PATH)
                    .get()
                    .asFile
                    .toURI()
        }
    }

    private fun registerPortalTasks(encodedCredentials: String) {
        val repoDirectory = project.layout.buildDirectory.dir(DEFAULT_REPO_DIR_PATH)
        val zipTask =
            project.tasks.register(ZIP_TASK_NAME, Zip::class.java) {
                group = "central publish"
                description = "Creates the Maven Central Portal deployment bundle."
                from(repoDirectory)
                archiveFileName.set("bundles.zip")
                destinationDirectory.set(project.layout.buildDirectory.dir("repos"))
                isPreserveFileTimestamps = false
                isReproducibleFileOrder = true
                dependsOn(
                    project.tasks
                        .withType(PublishToMavenRepository::class.java)
                        .matching { it.repository.name == "Local" },
                )
            }

        project.tasks.register(ROUTING_COMMAND, PublishToCentralPortalTask::class.java) {
            group = "central publish"
            description = "Uploads a deployment bundle to the Maven Central Portal."
            dependsOn(zipTask)
            bundleFile.set(zipTask.flatMap { it.archiveFile })
            authToken.set(encodedCredentials)
            baseUrl.set(
                project.providers
                    .gradleProperty(PORTAL_BASE_URL_PROPERTY)
                    .orElse(DEFAULT_PORTAL_BASE_URL),
            )
            publishingType.set(PUBLISHING_TYPE_USER_MANAGED)
            maxWaitSeconds.set(
                project.providers
                    .gradleProperty(MAX_WAIT_SECONDS_PROPERTY)
                    .map(String::toLong)
                    .orElse(DEFAULT_MAX_WAIT_SECONDS),
            )
            pollIntervalMillis.set(
                project.providers
                    .gradleProperty(POLL_INTERVAL_MILLIS_PROPERTY)
                    .map(String::toLong)
                    .orElse(DEFAULT_POLL_INTERVAL_MILLIS),
            )
        }
    }
}
