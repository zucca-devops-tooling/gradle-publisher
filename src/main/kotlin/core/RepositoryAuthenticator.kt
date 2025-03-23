package com.zucca.core

import org.gradle.api.Project
import java.net.Authenticator
import java.net.PasswordAuthentication

class RepositoryAuthenticator(private val usernameProperty: String, private val passwordProperty: String, private val project: Project): Authenticator() {

    override fun getPasswordAuthentication(): PasswordAuthentication {
        return PasswordAuthentication(getUsername(), getPassword().toCharArray())
    }

    fun getUsername(): String {
        if (project.hasProperty(usernameProperty)) {
            return project.findProperty(usernameProperty).toString()
        }

        return ""
    }

    fun getPassword(): String {
        if (project.hasProperty(passwordProperty)) {
            return project.findProperty(passwordProperty).toString()
        }

        return ""
    }
}