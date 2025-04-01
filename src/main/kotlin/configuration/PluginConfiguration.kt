package com.zucca.configuration

open class PluginConfiguration() {
    var releaseBranchPatterns: List<String> = emptyList()
    var usernameProperty: String = Defaults.USER_PROPERTY
    var passwordProperty: String = Defaults.PASS_PROPERTY
    var devRepoUrl: String = Defaults.DEV_REPO_URL
    var prodRepoUrl: String = Defaults.PROD_REPO_URL
    var gitFolder: String = "."
}