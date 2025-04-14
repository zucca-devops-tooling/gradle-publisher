package helpers

import dev.zuccaops.helpers.GitHelper
import io.mockk.*
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class GitHelperTest {
    private val project = mockk<Project>(relaxed = true)
    private val gitFolder = "."

    private val gitHelper = GitHelper(project, gitFolder)

    @AfterEach
    fun cleanup() {
        unmockkAll()
    }

    @Test
    fun `getBranch should return extracted branch from decorated git output`() {
        val output = ByteArrayOutputStream()
        output.write("HEAD -> origin/main".toByteArray())

        every { project.exec(any<Action<ExecSpec>>()) } answers {
            val spec = arg<Action<ExecSpec>>(0)
            val mockExecSpec = mockk<ExecSpec>(relaxed = true)
            every { mockExecSpec.standardOutput } returns output
            spec.execute(mockExecSpec)
            mockk<ExecResult>()
        }

        val result = gitHelper.getBranch()
        assertEquals("main", result)
    }

    @Test
    fun `isMainBranch should return true for main`() {
        val helper = GitHelper(project, gitFolder)

        // Set env var so getMainBranchName() returns it
        System.setProperty("CI_DEFAULT_BRANCH", "main")

        val result = helper.isMainBranch("main")

        assertEquals(true, result)

        // Cleanup
        System.clearProperty("CI_DEFAULT_BRANCH")
    }

    @Test
    fun `getMainBranchName should fallback to CI env vars`() {
        mockkStatic(System::class)
        every { System.getenv() } returns mapOf("GITHUB_REF_NAME" to "dev")

        val helper = GitHelper(project, gitFolder)
        val mainBranch = helper.javaClass.getDeclaredMethod("getMainBranchName")
        mainBranch.isAccessible = true
        val result = mainBranch.invoke(helper) as String

        assertEquals("dev", result)
    }
}