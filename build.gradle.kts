plugins {
    kotlin("jvm") version "1.9.22"
    `kotlin-dsl`
    id("com.zucca.gradle-publisher") version "1.0.0-SNAPSHOT"
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

publisher {
    devRepoUrl = "https://zuccadevops.jfrog.io/artifactory/publisher-libs-snapshot"
    usernameProperty = "jfrogUser"
    passwordProperty = "jfrogPassword"
}