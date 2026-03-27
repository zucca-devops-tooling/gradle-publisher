package testutil

import io.mockk.every
import io.mockk.mockk
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import java.io.File

object TestProjectFactory {
    fun create(): Project {
        val logger = mockk<Logger>(relaxed = true)
        val project = mockk<Project>(relaxed = true)
        val projectDir = File(".").canonicalFile

        every { project.logger } returns logger
        every { project.group } returns "dev.zucca-ops"
        every { project.name } returns "my-plugin"
        every { project.path } returns ":"
        every { project.version } returns "1.0.0"
        every { project.projectDir } returns projectDir
        every { project.rootDir } returns projectDir
        every { project.layout } returns mockk(relaxed = true)
        every { project.file(any<String>()) } answers { File(projectDir, firstArg<String>()).canonicalFile }

        return project
    }
}
