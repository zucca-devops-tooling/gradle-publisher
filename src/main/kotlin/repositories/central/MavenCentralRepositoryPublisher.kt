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
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
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
) : SonatypeRepositoryPublisher(project, versionResolver) {
    /**
     * Applies the `flying-gradle-plugin` and configures it.
     * Also finalizes the `publish` task with `publishToMavenCentralPortal`.
     */
    override fun configurePublishingRepository() {
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
        }

        super.configurePublishingRepository()

        project.logger.lifecycle("Applying tech.yanand.maven-central-publish plugin")
        project.pluginManager.apply("tech.yanand.maven-central-publish")

        val encodedCredentials = encodeBasicAuth(username, password)
        configureMavenCentralExtension(encodedCredentials)

        project.tasks.named("publish").configure {
            project.logger.lifecycle("⚙️ Routing 'publish' to 'publishToMavenCentralPortal' after finish")
            finalizedBy("publishToMavenCentralPortal")
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

    /**
     * Configures `flying-gradle-plugin` extension using reflection
     */
    @Suppress("UNCHECKED_CAST")
    private fun configureMavenCentralExtension(encodedCredentials: String) {
        project.logger.info("Configuring mavenCentral extension")
        project.extensions.configure(
            "mavenCentral",
            Action<Any> {
                val clazz = this.javaClass

                project.logger.debug("Setting repoDir to repos/bundles")
                val repoDir = project.layout.buildDirectory.dir("repos/bundles")

                clazz
                    .getMethod("getRepoDir")
                    .invoke(this)
                    .let { it as DirectoryProperty }
                    .set(repoDir)

                project.logger.debug("Setting authToken with encoded credentials")
                clazz
                    .getMethod("getAuthToken")
                    .invoke(this)
                    .let { it as Property<String> }
                    .set(encodedCredentials)

                project.logger.debug("Setting publishingType to USER_MANAGED")
                clazz
                    .getMethod("getPublishingType")
                    .invoke(this)
                    .let { it as Property<String> }
                    .set("USER_MANAGED")
            },
        )
    }
}
