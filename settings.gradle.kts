rootProject.name = "gradle-publisher"

val jfrogUser = gradle.startParameter.projectProperties["jfrogUser"]
val jfrogPassword = gradle.startParameter.projectProperties["jfrogPassword"]
pluginManagement {
    repositories {
        maven {
            url = uri("https://zuccadevops.jfrog.io/artifactory/publisher-libs-snapshot")

            credentials {
                username = jfrogUser
                password = jfrogPassword
            }
        }
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}