plugins {
    kotlin("jvm") version "1.9.22"
    `kotlin-dsl`
    id("dev.zucca-ops.gradle-publisher") version "0.0.1-maven-central-workaround-SNAPSHOT"
    id("java-gradle-plugin")
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
    signing
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
        customGradleCommand = "publishToSonatype"
    }

    usernameProperty = "ossrhUser"
    passwordProperty = "ossrhPassword"
    releaseBranchPatterns = listOf("PR-6")
}

afterEvaluate {
    extensions.configure<PublishingExtension> {
        publications {
            named<MavenPublication>("maven") {
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
                        connection.set("scm:git:git://github.com/zucca-devops-tooling/gradle-publisher.git")
                        developerConnection.set("scm:git:ssh://github.com/zucca-devops-tooling/gradle-publisher.git")
                        url.set("https://github.com/zucca-devops-tooling/gradle-publisher")
                    }
                }
            }
        }
    }
    // Signing
    signing {
        useInMemoryPgpKeys(
            System.getenv("SIGNING_KEY_ID"),
            System.getenv("SIGNING_KEY"),
            System.getenv("SIGNING_PASSWORD")
        )

        val publishing = extensions.getByType<PublishingExtension>()
        sign(publishing.publications["maven"])
    }
}

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            username.set(findProperty("ossrhUser") as String)
            password.set(findProperty("ossrhPassword") as String)
        }
    }
}