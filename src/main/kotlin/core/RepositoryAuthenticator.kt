package com.zucca.core

import com.zucca.configuration.PluginConfiguration
import org.gradle.api.Project
import java.net.Authenticator
import java.net.PasswordAuthentication

class RepositoryAuthenticator(private val configuration: PluginConfiguration, private val project: Project): Authenticator() {

    override fun getPasswordAuthentication(): PasswordAuthentication {
        return PasswordAuthentication(getUsername(), getPassword().toCharArray())
    }

    fun getUsername(): String {
        if (project.hasProperty(configuration.usernameProperty)) {
            return project.findProperty(configuration.usernameProperty).toString()
        }

        return ""
    }

    fun getPassword(): String {
        if (project.hasProperty(configuration.passwordProperty)) {
            return project.findProperty(configuration.passwordProperty).toString()
        }

        return ""
    }
}