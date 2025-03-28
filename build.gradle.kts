plugins {
    kotlin("jvm") version "1.9.22"
    `kotlin-dsl`
}

group = "com.zucca"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        url = uri("https://zuccadevops.jfrog.io/artifactory/publisher-libs-snapshot")
    }
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    implementation(gradleApi())
    implementation(localGroovy())
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

buildscript {
    repositories {
        maven {
            url = uri("https://zuccadevops.jfrog.io/artifactory/publisher-libs-snapshot")

            credentials {
                username = findProperty("jfrogUser") as String?
                password = findProperty("jfrogPassword") as String?
            }
        }
    }
    dependencies {
        classpath("com.zucca:gradle-publisher:1.0.0-SNAPSHOT")
    }
}

apply(plugin = "gradle-publisher")

publisher {
    devRepoUrl = "https://zuccadevops.jfrog.io/artifactory/publisher-libs-snapshot"
    usernameProperty = "jfrogUser"
    passwordProperty = "jfrogPassword"
}