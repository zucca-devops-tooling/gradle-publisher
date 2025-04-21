plugins {
    kotlin("jvm") version "2.1.20"
    `kotlin-dsl`
    id("dev.zucca-ops.gradle-publisher") version "0.1.1-PR-29-SNAPSHOT"
    id("java-gradle-plugin")
    signing
    id("com.diffplug.spotless") version "7.0.3"
    id("com.gradle.plugin-publish") version "1.2.1"
}

group = "dev.zucca-ops"
version = "0.1.1"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("io.mockk:mockk:1.13.7")
    testImplementation("io.mockk:mockk-agent-jvm:1.13.7")
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

@Suppress("UnstableApiUsage")
gradlePlugin {
    website = "https://github.com/zucca-devops-tooling/gradle-publisher"
    vcsUrl = "https://github.com/zucca-devops-tooling/gradle-publisher.git"
    plugins {
        create("gradlePublisherPlugin") {
            id = "dev.zucca-ops.gradle-publisher"
            implementationClass = "dev.zuccaops.GradlePublisherPlugin"
            displayName = "Gradle Publisher Plugin"
            tags = listOf("publishing", "ci", "versioning", "maven", "automation", "maven-central", "release")
            description = "A Gradle plugin that simplifies publishing by detecting environment and routing to the correct repository with dynamic versions."
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
afterEvaluate {
    tasks.matching { it.name == "publishPluginMavenPublicationToLocalRepository" }.configureEach {
        dependsOn("signMavenPublication")
    }
    tasks.matching { it.name == "publishMavenPublicationToLocalRepository" }.configureEach {
        dependsOn("signPluginMavenPublication")
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
    }

    alterProjectVersion = false
    usernameProperty = "mavenCentralUsername"
    passwordProperty = "mavenCentralPassword"
    releaseBranchPatterns = listOf("^release/\\d+\\.\\d+\\.\\d+$", "^hotfix/\\d+\\.\\d+\\.\\d+$")
    println("Resolved version: ${publisher.resolvedVersion}")
    println("Effective version: ${publisher.effectiveVersion}")
}

spotless {
    kotlin {
        target("src/main/**/*.kt")
        ktlint() // or prettier, diktat, etc.
        licenseHeader(
            """/*
 * Copyright 2025 GuidoZuccarelli
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
""".trimIndent()
        )
    }
}
