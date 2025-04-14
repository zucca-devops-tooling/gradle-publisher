package helpers

import dev.zuccaops.configuration.PluginConfiguration
import dev.zuccaops.helpers.GitHelper
import dev.zuccaops.helpers.VersionResolver
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import org.gradle.api.Project
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.test.Test

class VersionResolverTest {

    private val project = mockk<Project>(relaxed = true)

    @AfterEach
    fun cleanup() {
        unmockkAll()
    }

    @Test
    fun `adds snapshot suffix for non-release branches`() {
        every { project.version } returns "1.2.3"

        val mockConfig = mockk<PluginConfiguration>(relaxed = true)
        every { mockConfig.releaseBranchPatterns } returns listOf("^main$", "^release/\\d+\\.\\d+\\.\\d+$")

        mockkConstructor(GitHelper::class)
        every { anyConstructed<GitHelper>().getBranch() } returns "feature/cool-feature"

        val resolver = VersionResolver(project, mockConfig)

        val version = resolver.getVersion()
        assertEquals("1.2.3-feature-cool-feature-SNAPSHOT", version) // branch should be escaped as well (no slash allowed)
    }

    @Test
    fun `should keep version as-is on release branch`() {
        every { project.version } returns "2.0.0"
        val mockConfig = mockk<PluginConfiguration>(relaxed = true)
        every { mockConfig.releaseBranchPatterns } returns listOf("^main$", "^release/\\d+\\.\\d+\\.\\d+$")


        mockkConstructor(GitHelper::class)
        every { anyConstructed<GitHelper>().getBranch() } returns "release/2.0.0"

        val resolver = VersionResolver(project, mockConfig)

        val version = resolver.getVersion()
        assertEquals("2.0.0", version)
    }

    @Test
    fun `should consider main a branch release when no releaseBranchPatterns supplied`() {
        val mockConfig = mockk<PluginConfiguration>(relaxed = true)
        every { mockConfig.releaseBranchPatterns } returns emptyList()

        mockkConstructor(GitHelper::class)
        every { anyConstructed<GitHelper>().isMainBranch(any()) } returns true
        every { anyConstructed<GitHelper>().getBranch() } returns "some-main-branch"

        val resolver = VersionResolver(project, mockConfig)

        val isRelease = resolver.isRelease()
        assertEquals(true, isRelease)
    }

    @Test
    fun `should not consider the branch as release when no branch pattern supplied and branch is not main`() {
        val mockConfig = mockk<PluginConfiguration>(relaxed = true)
        every { mockConfig.releaseBranchPatterns } returns emptyList()

        mockkConstructor(GitHelper::class)
        every { anyConstructed<GitHelper>().isMainBranch(any()) } returns false
        every { anyConstructed<GitHelper>().getBranch() } returns "some-non-main-branch"

        val resolver = VersionResolver(project, mockConfig)

        val isRelease = resolver.isRelease()
        assertEquals(false, isRelease)
    }
}