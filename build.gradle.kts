plugins {
    kotlin("jvm") version "1.9.22"
    `kotlin-dsl`
    id("java-gradle-plugin")
    id("maven-publish")
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

gradlePlugin {
    plugins {
        create("gradlePublisherPlugin") {
            id = "com.zucca.gradle-publisher"
            implementationClass = "com.zucca.GradlePublisherPlugin"
        }
    }
}

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

    publications {
        create<MavenPublication>("manualPluginMarker") {
            groupId = "com.zucca"
            artifactId = "gradle-publisher.gradle.plugin"
            version = "1.0.0-SNAPSHOT"
            pom {
                packaging = "pom"
                withXml {
                    asNode().appendNode("dependencies").apply {
                        appendNode("dependency").apply {
                            appendNode("groupId", "com.zucca")
                            appendNode("artifactId", "gradle-publisher")
                            appendNode("version", "1.0.0-SNAPSHOT")
                        }
                    }
                }
            }
        }
    }
}