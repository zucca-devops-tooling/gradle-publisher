package helpers

import dev.zuccaops.helpers.GitHelper
import io.mockk.*
import org.gradle.api.Project
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junitpioneer.jupiter.SetEnvironmentVariable

class GitHelperTest {
    private val project = mockk<Project>(relaxed = true)
    private val gitFolder = "."

    private val gitHelper = spyk(GitHelper(project, gitFolder))

    @AfterEach
    fun cleanup() {
        unmockkAll()
    }

    @Test
    fun `getBranch should return extracted branch from decorated git output`() {
        every { gitHelper.executeGitCommand(any()) } returns "origin/main"

        val branch = gitHelper.getBranch()

        assertEquals("main", branch)
    }

    @Test
    fun `isMainBranch should return true when symbolicOutput matches`() {
        every { gitHelper
            .executeGitCommand(listOf("--git-dir=./.git", "symbolic-ref", "refs/remotes/origin/HEAD"))
        } returns "refs/remotes/origin/some-custom-main-branch"

        val result = gitHelper.isMainBranch("some-custom-main-branch")

        assertEquals(true, result)
    }

    @Test
    fun `isMainBranch should return false when symbolicOutput does not match`() {
        every { gitHelper
            .executeGitCommand(listOf("--git-dir=./.git", "symbolic-ref", "refs/remotes/origin/HEAD"))
        } returns "refs/remotes/origin/some-custom-main-branch"

        val result = gitHelper.isMainBranch("main")

        assertEquals(false, result)
    }

    @Test
    fun `isMainBranch should return true when remote show origin fallback matches`() {
        val multiLineGitResult = """
            * remote origin
              Fetch URL: https://github.com/zucca-devops-tooling/gradle-publisher.git
              Push  URL: https://github.com/zucca-devops-tooling/gradle-publisher.git
              HEAD branch: some-custom-main-branch
              Remote branches:
                main              tracked
                tests/plugin-core tracked
              Local branches configured for 'git pull':
                add-ci                           merges with remote add-ci
                feature/documentation            merges with remote feature/documentation
                feature/maven-central-workaround merges with remote feature/maven-central-workaround
                main                             merges with remote main
                tests/plugin-core                merges with remote tests/plugin-core
              Local refs configured for 'git push':
                main              pushes to main              (up to date)
                tests/plugin-core pushes to tests/plugin-core (fast-forwardable)
        """
        every {
            gitHelper.executeGitCommand(any())
        } returns ""

        every {
            gitHelper.executeGitCommand(match { it == listOf("remote", "show", "origin") })
        } returns multiLineGitResult

        val result = gitHelper.isMainBranch("some-custom-main-branch")

        assertEquals(true, result)
    }

    @Test
    fun `isMainBranch should return false when remote show origin fallback does not match`() {
        val multiLineGitResult = """
              HEAD branch: some-custom-main-branch
        """
        every {
            gitHelper.executeGitCommand(any())
        } returns ""

        every {
            gitHelper.executeGitCommand(match { it == listOf("remote", "show", "origin") })
        } returns multiLineGitResult

        val result = gitHelper.isMainBranch("main")

        assertEquals(false, result)
    }
    @Test
    @SetEnvironmentVariable(key = "GITHUB_REF_NAME", value = "dev")
    fun `isMainBranch should return true when fallback to CI env vars that match`() {
        every {
            gitHelper.executeGitCommand(any())
        } returns ""

        val result = gitHelper.isMainBranch("dev")
        assertEquals(true, result)
    }

    @Test
    @SetEnvironmentVariable(key = "CI_DEFAULT_BRANCH", value = "dev")
    fun `isMainBranch should return false when fallback to CI env vars that don't match`() {
        every {
            gitHelper.executeGitCommand(any())
        } returns ""

        val result = gitHelper.isMainBranch("main")
        assertEquals(false, result)
    }

    @Test
    fun `main or master should be the final fallbacks`() {
        every {
            gitHelper.executeGitCommand(any())
        } returns ""

        val result = gitHelper.isMainBranch("master")
        assertEquals(true, result)
    }
}