package dev.zucca_ops.repositories

import dev.zucca_ops.configuration.PluginConfiguration
import org.gradle.api.Project
import java.net.Authenticator
import java.net.PasswordAuthentication

class RepositoryAuthenticator(private val project: Project, private val configuration: PluginConfiguration): Authenticator() {

    override fun getPasswordAuthentication(): PasswordAuthentication? {
        val username: String? = getProdUsername()
        val password: String? = getProdPassword()

        if (username != null && password != null) {
            return PasswordAuthentication(username, password.toCharArray())
        }

        return null
    }

    fun getProdUsername(): String? {
        return getUsernameOrFallback(configuration.prod.usernameProperty)
    }

    fun getDevUsername(): String? {
        return getUsernameOrFallback(configuration.dev.usernameProperty)
    }

    fun getProdPassword(): String? {
        return getPasswordOrFallback(configuration.prod.passwordProperty)
    }

    fun getDevPassword(): String? {
        return getPasswordOrFallback(configuration.dev.passwordProperty)
    }

    private fun getUsernameOrFallback(usernamePropertyName: String?): String? {
        return getPropertyOrFallback(usernamePropertyName) { getFallbackUsername() }
    }

    private fun getPasswordOrFallback(passwordPropertyName: String?): String? {
        return getPropertyOrFallback(passwordPropertyName) { getFallbackPassword() }
    }

    private fun getFallbackUsername(): String? {
        return getProperty(configuration.usernameProperty)
    }

    private fun getFallbackPassword(): String? {
        return getProperty(configuration.passwordProperty)
    }

    private fun getPropertyOrFallback(propertyName: String?, fallbackFn: () -> String?): String? {
        val property: String? = getProperty(propertyName)

        return property ?: fallbackFn()
    }

    private fun getProperty(propertyName: String?): String? {
        return propertyName?.let { project.findProperty(it) as? String }
    }
}