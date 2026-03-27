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
package dev.zuccaops.repositories

import org.gradle.api.Project
import java.io.File
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

internal data class RepositoryCredentials(
    val username: String,
    val password: String,
)

internal sealed class ArtifactExistence {
    object Exists : ArtifactExistence()

    object Missing : ArtifactExistence()

    data class Unknown(
        val exception: Exception,
    ) : ArtifactExistence()
}

internal object ArtifactExistenceChecker {
    fun checkFile(path: File): ArtifactExistence = if (path.exists()) ArtifactExistence.Exists else ArtifactExistence.Missing

    fun checkHttp(
        uri: String,
        credentials: RepositoryCredentials? = null,
    ): ArtifactExistence {
        val connection = URL(uri).openConnection() as HttpURLConnection

        return try {
            credentials?.let { connection.setRequestProperty("Authorization", it.toBasicAuthHeader()) }
            connection.inputStream.use { it.readBytes() }
            ArtifactExistence.Exists
        } catch (_: FileNotFoundException) {
            ArtifactExistence.Missing
        } catch (exception: Exception) {
            ArtifactExistence.Unknown(exception)
        } finally {
            runCatching { connection.inputStream?.close() }
            runCatching { connection.errorStream?.close() }
            connection.disconnect()
        }
    }

    private fun RepositoryCredentials.toBasicAuthHeader(): String {
        val token = Base64.getEncoder().encodeToString("$username:$password".toByteArray(Charsets.UTF_8))
        return "Basic $token"
    }
}

internal fun Project.shouldPublishSnapshot(): Boolean {
    logger.lifecycle("Snapshot version detected, proceeding with publication")
    return true
}

internal fun Project.shouldPublishRelease(
    artifactExistence: ArtifactExistence,
    onUnknown: (Exception) -> Boolean = { throw it },
): Boolean =
    when (artifactExistence) {
        ArtifactExistence.Exists -> {
            logger.lifecycle("Production version already published, skipping tasks")
            false
        }

        ArtifactExistence.Missing -> {
            logger.lifecycle("Production version not published yet, proceeding with publication")
            true
        }

        is ArtifactExistence.Unknown -> {
            onUnknown(artifactExistence.exception)
        }
    }
