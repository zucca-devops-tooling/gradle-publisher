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
import dev.zuccaops.repositories.ArtifactExistence
import dev.zuccaops.repositories.ArtifactExistenceChecker
import dev.zuccaops.repositories.BaseRepositoryPublisher
import dev.zuccaops.repositories.shouldPublishRelease
import dev.zuccaops.repositories.shouldPublishSnapshot
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactRepositoryContainer
import org.gradle.internal.impldep.com.google.common.annotations.VisibleForTesting

abstract class SonatypeRepositoryPublisher(
    private val project: Project,
    private val versionResolver: VersionResolver,
) : BaseRepositoryPublisher(project, versionResolver) {
    private var isPublishable: Boolean? = null

    private fun artifactExistence(): ArtifactExistence {
        val artifactUri = getUri()
        project.logger.info("Checking if artifact exists at: $artifactUri")
        return ArtifactExistenceChecker.checkHttp(artifactUri)
    }

    @VisibleForTesting
    fun getUri(): String {
        val group = project.group.toString().replace(".", "/")
        val name = project.name.replace(".", "/")

        return "${ArtifactRepositoryContainer.MAVEN_CENTRAL_URL}$group/$name/${versionResolver.getVersion()}"
    }

    override fun isPublishable(): Boolean {
        if (isPublishable == null) {
            isPublishable =
                if (!versionResolver.isRelease()) {
                    project.shouldPublishSnapshot()
                } else {
                    project.shouldPublishRelease(artifactExistence()) { exception ->
                        project.logger.warn("Could not check if artifact is published: ${exception.message}")
                        true
                    }
                }
        }

        return isPublishable!!
    }
}
