plugins {
    kotlin("jvm") version "1.9.22"
    `kotlin-dsl`
    id("com.zucca.gradle-publisher") version "1.0.0-SNAPSHOT"
    id("java-gradle-plugin")
    //id("maven-publish")
}

group = "com.zucca"
version = "1.0.0"

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

gradlePlugin {
    plugins {
        create("gradlePublisherPlugin") {
            id = "com.zucca.gradle-publisher"
            implementationClass = "com.zucca.GradlePublisherPlugin"
        }
    }
}
/*
publishing {
    repositories {
        maven {
            name = "ArtifactorySnapshots"
            url = uri("https://zuccadevops.jfrog.io/artifactory/publisher-libs-snapshot")
            credentials {
                username = project.findProperty("jfrogUser") as String?
                password = project.findProperty("jfrogPassword") as String?
            }
        }
    }
}*/

publisher {
    devRepoUrl = "https://zuccadevops.jfrog.io/artifactory/publisher-libs-snapshot"
    usernameProperty = "jfrogUser"
    passwordProperty = "jfrogPassword"
}