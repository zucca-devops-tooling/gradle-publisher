package dev.zuccaops.repositories.central

import dev.zuccaops.helpers.VersionResolver
import dev.zuccaops.repositories.BaseRepositoryPublisher
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactRepositoryContainer
import org.gradle.internal.impldep.com.google.common.annotations.VisibleForTesting
import java.io.FileNotFoundException
import java.net.URL

abstract class SonatypeRepositoryPublisher(
    private val project: Project,
    private val versionResolver: VersionResolver) : BaseRepositoryPublisher(project, versionResolver) {

    /**
     * Checks if the artifact already exists in Maven Central to avoid re-uploading.
     */
    protected fun artifactAlreadyPublished(): Boolean {
        try {
            project.logger.info("Checking if artifact exists at: ${getUri()}")
            URL(getUri()).readBytes()
            project.logger.lifecycle("Production version already published, skipping tasks")

            return true
        }catch (e: FileNotFoundException) {
            project.logger.lifecycle("Production version not published yet, proceeding with publication")
            return false
        } catch (e: Exception) {
            project.logger.warn("⚠️ Could not check if artifact is published: ${e.message}")
            return false
        }
    }

    /**
     * Returns the Maven Central-style URI for this artifact, based on the
     * group, artifact name, and resolved version.
     *
     * This is used to check if the artifact is already published.
     *
     * @return the full artifact URI
     */
    @VisibleForTesting
    fun getUri(): String {
        val group = project.group.toString().replace(".", "/")
        val name = project.name.replace(".", "/")

        return "${ArtifactRepositoryContainer.MAVEN_CENTRAL_URL}$group/$name/${versionResolver.getVersion()}"
    }
}