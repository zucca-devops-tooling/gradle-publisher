package testutil

import io.mockk.every
import io.mockk.mockk
import org.gradle.api.Project
import org.gradle.api.logging.Logger

object TestProjectFactory {
    fun create(): Project {
        val logger = mockk<Logger>(relaxed = true)
        val project = mockk<Project>(relaxed = true)

        every { project.logger } returns logger
        every { project.group } returns "dev.zucca-ops"
        every { project.name } returns "my-plugin"
        every { project.version } returns "1.0.0"
        every { project.layout } returns mockk(relaxed = true)

        return project
    }
}