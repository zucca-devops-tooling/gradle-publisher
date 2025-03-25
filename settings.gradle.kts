rootProject.name = "gradle-publisher"

pluginManagement {
    repositories {
        maven {
            url = uri("https://zuccadevops.jfrog.io/artifactory/publisher-libs-snapshot")

            credentials {
                username = gradle.startParameter.projectProperties["jfrogUser"]
                password = gradle.startParameter.projectProperties["jfrogPassword"]
            }
        }
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}