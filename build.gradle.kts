plugins {
    kotlin("jvm") version "1.9.22"
    `kotlin-dsl`
    `maven-publish`
}

group = "com.zucca"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
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


publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
        }
    }

    repositories {
        maven {
            name = "ArtifactorySnapshots"

            // Your public JFrog Artifactory snapshot URL
            url = uri("https://zuccadevops.jfrog.io/artifactory/publisher-libs-snapshot")

            // Read from Gradle properties (passed via Jenkins -P flags or gradle.properties)
            credentials {
                username = project.findProperty("jfrogUser") as String?
                password = project.findProperty("jfrogPassword") as String?
            }
        }
    }
}