plugins {
    kotlin("jvm") version "1.9.22"
    `kotlin-dsl`
    id("dev.zucca-ops.gradle-publisher") version "0.0.1-PR-6-SNAPSHOT"
    id("java-gradle-plugin")
}

group = "dev.zucca-ops"
version = "0.0.1"

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
            id = "dev.zucca-ops.gradle-publisher"
            implementationClass = "dev.zucca_ops.GradlePublisherPlugin"
        }
    }
}

publisher {
    dev {
        target = "https://zuccadevops.jfrog.io/artifactory/publisher-libs-snapshot"
        usernameProperty = "jfrogUser"
        passwordProperty = "jfrogPassword"
    }
    prod {
        target = "mavenCentral"
    }

    usernameProperty = "ossrhUser"
    passwordProperty = "ossrhPassword"
}