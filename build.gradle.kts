import java.util.*

plugins {
    kotlin("jvm") version "1.9.22"
    `kotlin-dsl`
  //  id("dev.zucca-ops.gradle-publisher") version "0.0.1-SNAPSHOT"
    id("java-gradle-plugin")
    signing
    id("tech.yanand.maven-central-publish") version "1.2.0"
    id("maven-publish")
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
    val gpgHome = System.getProperty("gpg.homedir")?.toString()

    if (!keyId.isNullOrBlank() && !password.isNullOrBlank() && !gpgHome.isNullOrBlank()) {
        useGpgCmd()
        // Environment variable for GPG CLI to pick up keyring
        System.setProperty("GNUPGHOME", gpgHome)
        publishing.publications.withType<MavenPublication>().configureEach {
            signing.sign(this)
        }
    } else {
        logger.lifecycle("signing.keyId = ${keyId}")
        logger.lifecycle("signing.password = ${if (password.isNullOrBlank()) "MISSING" else "****"}")
        logger.lifecycle("gpg.homedir = ${gpgHome}")
        logger.warn("🔐 GPG signing skipped: missing keyId, password, or gpg.homedir")
    }
}
publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()

            from(components["java"])
        }
        repositories {
            // Starting from version 1.3.0, it does not need to configure the repository
            maven {
                name = "Local"
                url = layout.buildDirectory.dir("repos/bundles").get().asFile.toURI()
            }
        }
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

mavenCentral {
    // Starting from version 1.3.0, it does not need to configure this item
    repoDir.set(layout.buildDirectory.dir("repos/bundles"))
    // Token for Publisher API calls obtained from Sonatype official,
    // it should be Base64 encoded of "username:password".
    val user = findProperty("mavenCentralUsername")?.toString()
    val pass = findProperty("mavenCentralPassword")?.toString()

    if (!user.isNullOrBlank() && !pass.isNullOrBlank()) {
        val token = Base64.getEncoder().encodeToString("$user:$pass".toByteArray())
        authToken.set(token)
    } else {
        logger.warn("⚠️ Maven Central credentials missing; authToken not set")
    }
    // Whether the upload should be automatically published or not. Use 'USER_MANAGED' if you wish to do this manually.
    // This property is optional and defaults to 'AUTOMATIC'.
    publishingType.set("USER_MANAGED")
    // Max wait time for status API to get 'PUBLISHING' or 'PUBLISHED' status when the publishing type is 'AUTOMATIC',
    // or additionally 'VALIDATED' when the publishing type is 'USER_MANAGED'.
    // This property is optional and defaults to 60 seconds.
    maxWait = 60
}

/*
publisher {
    dev {
        target = "https://zucca.jfrog.io/artifactory/publisher-libs-snapshot"
        usernameProperty = "jfrogUser"
        passwordProperty = "jfrogPassword"
    }
    prod {
        target = "mavenCentral"
        customGradleCommand = "publishToMavenRepository"
    }

    usernameProperty = "mavenCentralUsername"
    passwordProperty = "mavenCentralPassword"
    releaseBranchPatterns = listOf("PR-6")
}
*/