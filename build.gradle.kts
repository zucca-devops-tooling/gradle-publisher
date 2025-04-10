plugins {
    kotlin("jvm") version "1.9.22"
    `kotlin-dsl`
    id("dev.zucca-ops.gradle-publisher") version "0.0.1-SNAPSHOT"
    id("java-gradle-plugin")
    signing
 //   id("tech.yanand.maven-central-publish") version "1.2.0" apply false
 //   id("maven-publish")
}

group = "dev.zucca-ops"
version = "0.0.1"

repositories {
    mavenCentral()
    maven {
        url = uri("https://zucca.jfrog.io/artifactory/publisher-libs-snapshot")
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

signing {
    val signingKeyPath = findProperty("signing.secretKeyFile")?.toString()
    val signingPassword = findProperty("signing.password") as String?

    if (!signingKeyPath.isNullOrBlank() && !signingPassword.isNullOrBlank()) {
        val keyContent = File(signingKeyPath).readText()
        useInMemoryPgpKeys(keyContent, signingPassword)
        sign(publishing.publications["maven"])
    } else {
        logger.warn("🔐 GPG signing skipped: missing key or password")
    }
}

publisher {
    dev {
        target = "https://zucca.jfrog.io/artifactory/publisher-libs-snapshot"
        usernameProperty = "jfrogUser2"
        passwordProperty = "jfrogPassword2"
    }
    prod {
        target = "mavenCentral"
        customGradleCommand = "publishToMavenRepository"
    }

    usernameProperty = "ossrhUser"
    passwordProperty = "ossrhPassword"
    releaseBranchPatterns = listOf("PR-6")
}
