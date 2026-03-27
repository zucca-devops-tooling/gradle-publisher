package helpers

import dev.zuccaops.helpers.GitHelper
import io.mockk.*
import org.gradle.api.GradleException
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
        every { gitHelper.ensureGitContextAvailable() } just Runs
        every { gitHelper.getBranchByCIEnv() } returns null
        every { gitHelper.executeGitCommand(any()) } returns "origin/main"

        // when
        val branch = gitHelper.getBranch()

        // then
        assertEquals("main", branch)
    }

    @Test
    fun `getBranch should return extracted LOCAL branch from decorated git output`() {
        // given
        every { gitHelper.ensureGitContextAvailable() } just Runs
        every { gitHelper.getBranchByCIEnv() } returns null
        every { gitHelper.executeGitCommand(any()) } returns "HEAD&any-dev-branch"

        // when
        val branch = gitHelper.getBranch()

        // then
        assertEquals("any-dev-branch", branch)
    }

    @Test
    fun `getBranch should ignore grafted and symbolic remote HEAD decorations`() {
        // given
        every { gitHelper.ensureGitContextAvailable() } just Runs
        every { gitHelper.getBranchByCIEnv() } returns null
        every { gitHelper.executeGitCommand(any()) } returns "grafted#origin/HEAD&origin/main#origin/chore/ci-migration"

        // when
        val branch = gitHelper.getBranch()

        // then
        assertEquals("chore/ci-migration", branch)
    }

    @Test
    fun `getBranch should prefer CI branch from GitHub Actions`() {
        // given
        every { gitHelper.ensureGitContextAvailable() } just Runs
        every { gitHelper.getBranchByCIEnv() } returns "chore/ci-migration"

        // when
        val branch = gitHelper.getBranch()

        // then
        assertEquals("chore/ci-migration", branch)
        verify(exactly = 0) { gitHelper.executeGitCommand(any()) }
    }

    @Test
    fun `getBranchByCIEnv should prefer GitHub head ref over pull request ref`() {
        // given
        val envVars =
            mapOf(
                "GITHUB_HEAD_REF" to "chore/ci-migration",
                "GITHUB_REF" to "refs/pull/123/merge",
            )

        // when
        val branch = gitHelper.getBranchByCIEnv(envVars)

        // then
        assertEquals("chore/ci-migration", branch)
    }

    @Test
    fun `isMainBranch should return true when symbolicOutput matches`() {
        // given
        every { gitHelper.ensureGitContextAvailable() } just Runs
        every {
            gitHelper.executeGitCommand(match {
                it.takeLast(2) == listOf("symbolic-ref", "refs/remotes/origin/HEAD")
            })
        } returns "refs/remotes/origin/some-custom-main-branch"

        // when
        val result = gitHelper.isMainBranch("some-custom-main-branch")

        // then
        assertTrue(result)
    }

    @Test
    fun `isMainBranch should return false when symbolicOutput does not match`() {
        // given
        every { gitHelper.ensureGitContextAvailable() } just Runs
        every {
            gitHelper.executeGitCommand(match {
                it.takeLast(2) == listOf("symbolic-ref", "refs/remotes/origin/HEAD")
            })
        } returns "refs/remotes/origin/some-custom-main-branch"

        // when
        val result = gitHelper.isMainBranch("main")

        // then
        assertFalse(result)
    }

    @Test
    fun `isMainBranch should return true when remote show origin fallback matches`() {
        // given
        every { gitHelper.ensureGitContextAvailable() } just Runs
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
            gitHelper.executeGitCommand(match { it.takeLast(3) == listOf("remote", "show", "origin") })
        } returns multiLineGitResult

        // when
        val result = gitHelper.isMainBranch("some-custom-main-branch")

        // then
        assertTrue(result)
    }

    @Test
    fun `isMainBranch should return false when remote show origin fallback does not match`() {
        // given
        every { gitHelper.ensureGitContextAvailable() } just Runs
        val multiLineGitResult = """
              HEAD branch: some-custom-main-branch
        """
        every {
            gitHelper.executeGitCommand(any())
        } returns ""
        every {
            gitHelper.executeGitCommand(match { it.takeLast(3) == listOf("remote", "show", "origin") })
        } returns multiLineGitResult

        // when
        val result = gitHelper.isMainBranch("main")

        // then
        assertFalse(result)
    }
    @Test
    fun `isMainBranch should return true when fallback to CI env vars that match`() {
        // given
        every { gitHelper.ensureGitContextAvailable() } just Runs
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
        every { gitHelper.ensureGitContextAvailable() } just Runs
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
        every { gitHelper.ensureGitContextAvailable() } just Runs
        every {
            gitHelper.executeGitCommand(any())
        } returns ""
        every { gitHelper.getDefaultBranchByCIEnv() } returns null

        // when
        val result = gitHelper.isMainBranch("master")

        // then
        assertTrue(result)
    }

    @Test
    fun `getBranch should fail with a comprehensive message when git folder does not exist`() {
        // given
        val helper = GitHelper(project, "build/missing-git-context-for-test")

        // when
        val exception = assertThrows(GradleException::class.java) { helper.getBranch() }

        // then
        assertTrue(exception.message!!.contains("publisher.gitFolder"))
        assertTrue(exception.message!!.contains("does not exist"))
        assertTrue(exception.message!!.contains("Run the build from a real Git checkout"))
    }

    @Test
    fun `getBranch should fail with git diagnostics when repository validation fails`() {
        // given
        every {
            gitHelper.executeGitCommandResult(match {
                it.takeLast(2) == listOf("rev-parse", "--is-inside-work-tree")
            })
        } returns GitHelper.GitCommandResult("", "fatal: not a git repository", 128)

        // when
        val exception = assertThrows(GradleException::class.java) { gitHelper.getBranch() }

        // then
        assertTrue(exception.message!!.contains("Git repository validation failed"))
        assertTrue(exception.message!!.contains("stderr: fatal: not a git repository"))
        assertTrue(exception.message!!.contains("Ensure the git executable is installed"))
    }
}
