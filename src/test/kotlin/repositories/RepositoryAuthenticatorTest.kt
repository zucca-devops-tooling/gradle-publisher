package repositories

import dev.zuccaops.configuration.PluginConfiguration
import dev.zuccaops.helpers.publisherConfiguration
import dev.zuccaops.repositories.RepositoryAuthenticator
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import testutil.TestProjectFactory

class RepositoryAuthenticatorTest {

    private val project = TestProjectFactory.create()

    @Test
    fun `should return prod credentials from explicit properties`() {
        // given
        every { project.findProperty("prodUser") } returns "guido"
        every { project.findProperty("prodPass") } returns "hunter2"

        val config = mockk<PluginConfiguration>(relaxed = true) {
            every { prod.usernameProperty } returns "prodUser"
            every { prod.passwordProperty } returns "prodPass"
        }

        every { project.publisherConfiguration() } returns config

        val authenticator = RepositoryAuthenticator(project)

        // when
        val username = authenticator.getProdUsername()
        val password = authenticator.getProdPassword()

        // then
        assertEquals("guido", username)
        assertEquals("hunter2", password)
    }

    @Test
    fun `should fallback to global credentials if not in prod config`() {
        // given
        every { project.findProperty("globalUser") } returns "globaluser"
        every { project.findProperty("globalPass") } returns "globalpass"
        every { project.findProperty("") } returns null

        val config = mockk<PluginConfiguration>(relaxed = true) {
            every { prod.usernameProperty } returns ""
            every { prod.passwordProperty } returns ""
            every { usernameProperty } returns "globalUser"
            every { passwordProperty } returns "globalPass"
        }

        every { project.publisherConfiguration() } returns config

        val authenticator = RepositoryAuthenticator(project)

        // when
        val username = authenticator.getProdUsername()
        val password = authenticator.getProdPassword()

        // then
        assertEquals("globaluser", username)
        assertEquals("globalpass", password)
    }
}