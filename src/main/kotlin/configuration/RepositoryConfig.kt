package dev.zucca_ops.configuration

open class RepositoryConfig {
    var target: String = Defaults.TARGET
    var usernameProperty: String = Defaults.USER_PROPERTY
    var passwordProperty: String = Defaults.PASS_PROPERTY
    var customGradleCommand: String? = null
    var sign: Boolean = false
}