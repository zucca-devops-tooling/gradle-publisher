/*
 * Copyright 2025 the original author or authors.
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
package dev.zuccaops.repositories.local

import dev.zuccaops.helpers.VersionResolver
import dev.zuccaops.repositories.BaseRepositoryPublisher
import dev.zuccaops.repositories.RepositoryConstants
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.kotlin.dsl.get
import java.io.File

/**
 * Publisher for local Maven repository (`~/.m2/repository`).
 *
 * This implementation is useful for dry-runs, local caching, or plugin development.
 *
 * - For release builds, it checks if the artifact already exists and skips re-publishing if found.
 * - It never applies signing.
 * - It registers `mavenLocal()` as the publishing target.
 *
 * @param project The current Gradle project.
 * @param versionResolver Utility to determine version and release state.
 *
 * @author Guido Zuccarelli
 */
class LocalRepositoryPublisher(
    private val project: Project,
    private val versionResolver: VersionResolver,
) : BaseRepositoryPublisher(project, versionResolver) {
    override fun isPublishable(): Boolean {
        if (versionResolver.isRelease()) {
            val m2Repo = File(System.getProperty("user.home"), RepositoryConstants.LOCAL_MAVEN_PATH)
            val groupPath = project.group.toString().replace('.', '/')
            val artifactId = project.name
            val version = versionResolver.getVersion()

            val artifactPath = File(m2Repo, "$groupPath/$artifactId/$version/$artifactId-$version.jar")

            return artifactPath.exists()
        }

        return true
    }

    /**
     * Always disables GPG signing for local repository publishing.
     *
     * @return `false`
     */
    override fun shouldSign(): Boolean = false

    /**
     * Configures the repository to use `mavenLocal()` and removes any preconfigured Sonatype repository
     * that may be auto-injected by plugins like `nexus-publish`.
     *
     * @param repositoryHandler Gradle's repository handler for the publishing block.
     */
    override fun registerRepository(repositoryHandler: RepositoryHandler) {
        repositoryHandler.mavenLocal()
        repositoryHandler.removeIf { it.name == "sonatype" }
    }
}
