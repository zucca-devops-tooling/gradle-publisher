plugins {
    kotlin("jvm") version "1.9.22"
    `kotlin-dsl`
    `gradle-publisher`
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
    implementation("com.zucca:gradle-publisher:1.0.0-SNAPSHOT")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}