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

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.net.URI
import java.time.Duration

@DisableCachingByDefault(because = "Uploads a deployment bundle to an external service")
abstract class PublishToCentralPortalTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val bundleFile: RegularFileProperty

    @get:Input
    abstract val authToken: Property<String>

    @get:Input
    abstract val baseUrl: Property<String>

    @get:Input
    abstract val publishingType: Property<String>

    @get:Input
    abstract val maxWaitSeconds: Property<Long>

    @get:Input
    abstract val pollIntervalMillis: Property<Long>

    @TaskAction
    fun upload() {
        val status =
            try {
                CentralPortalClient(
                    baseUri = URI.create(baseUrl.get()),
                    authToken = authToken.get(),
                ).uploadAndWait(
                    bundle = bundleFile.get().asFile.toPath(),
                    publishingType = publishingType.get(),
                    timeout = Duration.ofSeconds(maxWaitSeconds.get()),
                    pollInterval = Duration.ofMillis(pollIntervalMillis.get()),
                )
            } catch (exception: CentralPortalException) {
                throw GradleException(exception.message ?: "Central Portal upload failed.", exception)
            }

        logger.lifecycle("Upload file success! current status: {}.", status.state)
    }
}
