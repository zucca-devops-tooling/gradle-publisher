plugins {
    kotlin("jvm") version "1.9.22"
    `kotlin-dsl`
    id("dev.zucca-ops.gradle-publisher") version "0.0.1-maven-central-workaround-SNAPSHOT"
    id("java-gradle-plugin")
    id("com.moengage.plugin.maven.publish") version "1.0.0"
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
        customGradleCommand = "publishToMavenRepository"
    }

    usernameProperty = "ossrhUser"
    passwordProperty = "ossrhPassword"
    releaseBranchPatterns = listOf("PR-6")
}
