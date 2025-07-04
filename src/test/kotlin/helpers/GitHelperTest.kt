package helpers

import dev.zuccaops.helpers.GitHelper
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import testutil.TestProjectFactory

class GitHelperTest {
    private val project = TestProjectFactory.create()
    private val gitFolder = "."

    private val gitHelper = spyk(GitHelper(project, gitFolder))

    @AfterEach
    fun cleanup() {
        unmockkAll()
    }

    @Test
    fun `getBranch should return extracted branch from decorated git output`() {
        // given
        every { gitHelper.executeGitCommand(any()) } returns "origin/main"

        // when
        val branch = gitHelper.getBranch()

        // then
        assertEquals("main", branch)
    }

    @Test
    fun `getBranch should return extracted LOCAL branch from decorated git output`() {
        // given
        every { gitHelper.executeGitCommand(any()) } returns "HEAD&any-dev-branch"

        // when
        val branch = gitHelper.getBranch()

        // then
        assertEquals("any-dev-branch", branch)
    }

    @Test
    fun `isMainBranch should return true when symbolicOutput matches`() {
        // given
        every { gitHelper
            .executeGitCommand(listOf("--git-dir=./.git", "symbolic-ref", "refs/remotes/origin/HEAD"))
        } returns "refs/remotes/origin/some-custom-main-branch"

        // when
        val result = gitHelper.isMainBranch("some-custom-main-branch")

        // then
        assertTrue(result)
    }

    @Test
    fun `isMainBranch should return false when symbolicOutput does not match`() {
        // given
        every { gitHelper
            .executeGitCommand(listOf("--git-dir=./.git", "symbolic-ref", "refs/remotes/origin/HEAD"))
        } returns "refs/remotes/origin/some-custom-main-branch"

        // when
        val result = gitHelper.isMainBranch("main")

        // then
        assertFalse(result)
    }

    @Test
    fun `isMainBranch should return true when remote show origin fallback matches`() {
        // given
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

        // when
        val result = gitHelper.isMainBranch("some-custom-main-branch")

        // then
        assertTrue(result)
    }

    @Test
    fun `isMainBranch should return false when remote show origin fallback does not match`() {
        // given
        val multiLineGitResult = """
              HEAD branch: some-custom-main-branch
        """
        every {
            gitHelper.executeGitCommand(any())
        } returns ""
        every {
            gitHelper.executeGitCommand(match { it == listOf("remote", "show", "origin") })
        } returns multiLineGitResult

        // when
        val result = gitHelper.isMainBranch("main")

        // then
        assertFalse(result)
    }
    @Test
    fun `isMainBranch should return true when fallback to CI env vars that match`() {
        // given
        every {
            gitHelper.executeGitCommand(any())
        } returns ""
        every { gitHelper.getDefaultBranchByCIEnv() } returns "dev"

        // when
        val result = gitHelper.isMainBranch("dev")

        // then
        assertTrue(result)
    }

    @Test
    fun `isMainBranch should return false when fallback to CI env vars that don't match`() {
        // given
        every {
            gitHelper.executeGitCommand(any())
        } returns ""
        every { gitHelper.getDefaultBranchByCIEnv() } returns "dev"

        // when
        val result = gitHelper.isMainBranch("main")

        // then
        assertFalse(result)
    }

    @Test
    fun `main or master should be the final fallbacks`() {
        // given
        every {
            gitHelper.executeGitCommand(any())
        } returns ""
        every { gitHelper.getDefaultBranchByCIEnv() } returns null

        // when
        val result = gitHelper.isMainBranch("master")

        // then
        assertTrue(result)
    }
}