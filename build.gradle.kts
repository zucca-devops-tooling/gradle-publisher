import java.util.*

plugins {
    kotlin("jvm") version "1.9.22"
    `kotlin-dsl`
    id("dev.zucca-ops.gradle-publisher") version "0.0.1-SNAPSHOT"
    id("java-gradle-plugin")
    signing
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
    implementation("tech.yanand.maven-central-publish:tech.yanand.maven-central-publish.gradle.plugin:1.2.0")
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
    val keyId = findProperty("signing.keyId") as String?
    val password = findProperty("signing.password") as String?
    val keyPath = findProperty("signing.secretKeyRingFile")?.toString()

    if (!keyId.isNullOrBlank() && !password.isNullOrBlank() && !keyPath.isNullOrBlank()) {
        logger.lifecycle("🔐 Using GPG secret key file at $keyPath")
        useInMemoryPgpKeys(File(keyPath).readText(), password)
        publishing.publications.withType<MavenPublication>().configureEach {
            signing.sign(this)
        }
    } else {
        logger.warn("🔐 File-based signing skipped: missing keyId, password, or key file")
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("Gradle Publisher")
            description.set("A Gradle plugin that simplifies publishing by detecting environment and routing to the correct repository with dynamic versions.")
            url.set("https://github.com/zucca-devops-tooling/gradle-publisher")

            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    distribution.set("repo")
                }
            }

            developers {
                developer {
                    id.set("zucca")
                    name.set("Guido Zuccarelli")
                    email.set("guidozuccarelli@hotmail.com")
                }
            }

            scm {
                url.set("https://github.com/zucca-devops-tooling/gradle-publisher")
                connection.set("scm:git:git://github.com/zucca-devops-tooling/gradle-publisher.git")
                developerConnection.set("scm:git:ssh://github.com/zucca-devops-tooling/gradle-publisher.git")
            }
        }
    }
}

java {
    withJavadocJar()
    withSourcesJar()
}

publisher {
    dev {
        target = "https://zucca.jfrog.io/artifactory/publisher-libs-snapshot"
        usernameProperty = "jfrogUser"
        passwordProperty = "jfrogPassword"
        sign = false
    }
    prod {
        target = "mavenCentral"
        customGradleCommand = "publishToMavenRepository"
    }

    usernameProperty = "mavenCentralUsername"
    passwordProperty = "mavenCentralPassword"
    //releaseBranchPatterns = listOf("PR-6")
}
