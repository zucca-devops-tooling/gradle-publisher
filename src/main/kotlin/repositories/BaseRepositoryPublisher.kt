/*
 * Copyright 2024 the original author or authors.
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
package dev.zuccaops.repositories

import dev.zuccaops.helpers.VersionResolver
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import org.gradle.plugins.signing.Sign

abstract class BaseRepositoryPublisher(
    private val project: Project,
    private val versionResolver: VersionResolver,
) : RepositoryPublisher {
    override fun configurePublishingRepository() {
        project.configure<PublishingExtension> {
            publications {
                create<MavenPublication>("maven") {
                    groupId = project.group.toString()
                    artifactId = project.name
                    from(project.components["java"])
                    version = versionResolver.getVersion()
                }
            }

            registerRepository(this.repositories)

            project.version = versionResolver.getVersion()
            project.tasks.withType(PublishToMavenRepository::class.java).configureEach {
                onlyIf {
                    isPublishable()
                }
            }
            project.tasks.withType(Sign::class.java).configureEach {
                onlyIf {
                    shouldSign()
                }
            }
        }
    }

    protected abstract fun isPublishable(): Boolean

    protected abstract fun shouldSign(): Boolean

    protected abstract fun registerRepository(repositoryHandler: RepositoryHandler)
}
