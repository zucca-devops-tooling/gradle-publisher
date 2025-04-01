package dev.zucca_ops.configuration

import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

open class PluginConfiguration @Inject constructor(objects: ObjectFactory) {
    val dev = objects.newInstance(RepositoryConfig::class.java)
    val prod = objects.newInstance(RepositoryConfig::class.java)

    fun dev(configure: RepositoryConfig.() -> Unit) {
        dev.configure()
    }

    fun prod(configure: RepositoryConfig.() -> Unit) {
        prod.configure()
    }

    // Shared fallback credentials
    var usernameProperty: String? = Defaults.USER_PROPERTY
    var passwordProperty: String? = Defaults.PASS_PROPERTY

    var gitFolder: String = Defaults.GIT_FOLDER
    var releaseBranchPatterns: List<String> = Defaults.RELEASE_BRANCH_REGEXES
}