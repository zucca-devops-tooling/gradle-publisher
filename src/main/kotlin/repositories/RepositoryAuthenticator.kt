/*
 * Copyright 2025 GuidoZuccarelli
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.zuccaops.repositories

import dev.zuccaops.configuration.Defaults
import dev.zuccaops.configuration.PluginConfiguration
import dev.zuccaops.helpers.publisherConfiguration
import org.gradle.api.Project
import java.net.Authenticator
import java.net.PasswordAuthentication

/**
 * Authenticates against remote repositories by resolving credentials from
 * project properties, using a fallback hierarchy for dev and prod environments.
 *
 * This class also integrates with `HttpURLConnection` when authentication
 * is needed at runtime via `Authenticator.setDefault(...)`.
 *
 * @property project the current Gradle project
 *
 * @author Guido Zuccarelli
 */
class RepositoryAuthenticator(
    private val project: Project,
) : Authenticator() {
    private val configuration: PluginConfiguration = project.publisherConfiguration()

    /**
     * Automatically invoked when a remote connection requires authentication.
     * Provides production credentials by default.
     *
     * @return the password authentication object or null if credentials are missing
     */
    override fun getPasswordAuthentication(): PasswordAuthentication? {
        val username: String? = getProdUsername()
        val password: String? = getProdPassword()

        if (username != null && password != null) {
            project.logger.debug("Requesting to repository with credentials")
            return PasswordAuthentication(username, password.toCharArray())
        }

        project.logger.debug("Credentials could not be found, requesting to repository without credentials")

        return null
    }

    fun getProdUsername(): String? = getUsernameOrFallback(configuration.prod.usernameProperty)

    fun getDevUsername(): String? = getUsernameOrFallback(configuration.dev.usernameProperty)

    fun getProdPassword(): String? = getPasswordOrFallback(configuration.prod.passwordProperty)

    fun getDevPassword(): String? = getPasswordOrFallback(configuration.dev.passwordProperty)

    private fun getUsernameOrFallback(usernamePropertyName: String): String? =
        getPropertyOrFallback(usernamePropertyName) {
            getFallbackUsername()
        }

    private fun getPasswordOrFallback(passwordPropertyName: String): String? =
        getPropertyOrFallback(passwordPropertyName) {
            getFallbackPassword()
        }

    private fun getFallbackUsername(): String? = getProperty(configuration.usernameProperty)

    private fun getFallbackPassword(): String? = getProperty(configuration.passwordProperty)

    private fun getPropertyOrFallback(
        propertyName: String,
        fallbackFn: () -> String?,
    ): String? {
        val property: String? = getProperty(propertyName)

        return property ?: fallbackFn()
    }

    private fun getProperty(propertyName: String): String? {
        val property = project.findProperty(propertyName)

        if (isDefaultProperty(propertyName)) {
            project.logger.debug("Credential not configured, attempting to find default: $propertyName")
        }

        if (property != null) {
            project.logger.debug("Credential $propertyName found")
            return property as String
        }

        if (!isDefaultProperty(propertyName)) {
            project.logger.warn("⚠️ Configured credential '$propertyName' could not be found")
        }

        return null
    }

    private fun isDefaultProperty(propertyName: String): Boolean =
        propertyName == Defaults.USER_PROPERTY || propertyName == Defaults.PASS_PROPERTY
}
