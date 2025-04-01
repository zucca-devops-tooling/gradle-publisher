package dev.zucca_ops.configuration

object Defaults {
    const val TARGET = "local"
    const val USER_PROPERTY = "mavenUsername"
    const val PASS_PROPERTY = "mavenPassword"
    const val GIT_FOLDER = "."
    val RELEASE_BRANCH_REGEXES = listOf(
        """^release/\d+\.\d+\.\d+$""",
        """^v\d+\.\d+\.\d+$"""
    )
}