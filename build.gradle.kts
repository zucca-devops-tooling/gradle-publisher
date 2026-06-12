plugins {
    id("dev.zucca-ops.gradle-publisher") version "1.1.0"
    id("java-gradle-plugin")
    id("com.diffplug.spotless") version "8.3.0"
    id("com.gradle.plugin-publish") version "2.0.0"
    kotlin("jvm") version "2.1.21"
    `kotlin-dsl`
    signing
}

group = "dev.zucca-ops"
version = "1.1.1"

val integrationTestCandidateVersion = "0.0.0-integration-test"
val integrationTestRepositoryDirectory = layout.buildDirectory.dir("integration-test-repository")
val integrationTestConsumerDirectory = layout.buildDirectory.dir("integration-test-consumers")

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
    testImplementation("io.mockk:mockk:1.14.6")
    testImplementation("io.mockk:mockk-agent-jvm:1.14.6")
    implementation(gradleApi())
    implementation(localGroovy())
}

val functionalTestSourceSet = sourceSets.create("functionalTest")
val integrationTestSourceSet = sourceSets.create("integrationTest")

configurations[functionalTestSourceSet.implementationConfigurationName]
    .extendsFrom(configurations.testImplementation.get())
configurations[functionalTestSourceSet.runtimeOnlyConfigurationName]
    .extendsFrom(configurations.testRuntimeOnly.get())
configurations[integrationTestSourceSet.implementationConfigurationName]
    .extendsFrom(configurations.testImplementation.get())
configurations[integrationTestSourceSet.runtimeOnlyConfigurationName]
    .extendsFrom(configurations.testRuntimeOnly.get())

dependencies {
    add(functionalTestSourceSet.implementationConfigurationName, gradleTestKit())
    add(integrationTestSourceSet.implementationConfigurationName, gradleTestKit())
}

tasks.test {
    useJUnitPlatform()
}

val functionalTest =
    tasks.register<Test>("functionalTest") {
        description = "Runs functional tests with Gradle TestKit."
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        testClassesDirs = functionalTestSourceSet.output.classesDirs
        classpath = functionalTestSourceSet.runtimeClasspath
        useJUnitPlatform()
        shouldRunAfter(tasks.test)
    }

val cleanIntegrationTestRepository =
    tasks.register<Delete>("cleanIntegrationTestRepository") {
        description = "Deletes the isolated Maven repository used by integration tests."
        group = LifecycleBasePlugin.BUILD_GROUP
        delete(integrationTestRepositoryDirectory)
    }

val cleanIntegrationTestConsumers =
    tasks.register<Delete>("cleanIntegrationTestConsumers") {
        description = "Deletes standalone consumer builds created by integration tests."
        group = LifecycleBasePlugin.BUILD_GROUP
        delete(integrationTestConsumerDirectory)
    }

val candidatePublicationTasks =
    listOf(
        "publishIntegrationTestPluginMavenPublicationToIntegrationTestRepository",
        "publishIntegrationTestPluginMarkerMavenPublicationToIntegrationTestRepository",
    )

tasks.matching { it.name in candidatePublicationTasks }.configureEach {
    dependsOn(cleanIntegrationTestRepository)
}

val prepareIntegrationTestRepository =
    tasks.register("prepareIntegrationTestRepository") {
        description = "Publishes the packaged candidate plugin into an isolated Maven repository."
        group = LifecycleBasePlugin.BUILD_GROUP
        dependsOn(candidatePublicationTasks)
        outputs.dir(integrationTestRepositoryDirectory)
    }

val integrationTest =
    tasks.register<Test>("integrationTest") {
        description = "Runs integration tests against packaged candidate plugin artifacts."
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        testClassesDirs = integrationTestSourceSet.output.classesDirs
        classpath = integrationTestSourceSet.runtimeClasspath
        useJUnitPlatform()
        dependsOn(prepareIntegrationTestRepository, cleanIntegrationTestConsumers)
        shouldRunAfter(functionalTest)
        inputs.dir(integrationTestRepositoryDirectory)
        inputs.property("candidateVersion", integrationTestCandidateVersion)
        systemProperty("integrationTest.candidateVersion", integrationTestCandidateVersion)

        doFirst {
            systemProperty(
                "integrationTest.repository",
                integrationTestRepositoryDirectory.get().asFile.absolutePath,
            )
            systemProperty(
                "integrationTest.consumerDirectory",
                integrationTestConsumerDirectory.get().asFile.absolutePath,
            )
        }
    }

tasks.check {
    dependsOn(functionalTest, integrationTest)
}

kotlin {
    jvmToolchain(17)
}

gradlePlugin {
    testSourceSets(functionalTestSourceSet)
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
        publishing.publications.withType<MavenPublication>().matching {
            !name.startsWith("integrationTest")
        }.configureEach {
            signing.sign(this)
        }
    } else {
        logger.warn("🔐 File-based signing skipped: missing keyId, password, or key file")
    }
}

publishing {
    publications {
        create<MavenPublication>("integrationTestPluginMaven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = integrationTestCandidateVersion
            from(components["java"])
        }

        create<MavenPublication>("integrationTestPluginMarkerMaven") {
            groupId = "dev.zucca-ops.gradle-publisher"
            artifactId = "dev.zucca-ops.gradle-publisher.gradle.plugin"
            version = integrationTestCandidateVersion

            pom.withXml {
                val dependencies = asNode().appendNode("dependencies")
                val dependency = dependencies.appendNode("dependency")
                dependency.appendNode("groupId", project.group.toString())
                dependency.appendNode("artifactId", project.name)
                dependency.appendNode("version", integrationTestCandidateVersion)
            }
        }
    }

    repositories {
        maven {
            name = "integrationTest"
            url = integrationTestRepositoryDirectory.get().asFile.toURI()
        }
    }

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

tasks.withType<org.gradle.api.publish.maven.tasks.PublishToMavenRepository>().configureEach {
    val isCandidatePublication = name.startsWith("publishIntegrationTest")
    val isIntegrationTestRepository = name.endsWith("ToIntegrationTestRepository")

    when {
        isCandidatePublication && isIntegrationTestRepository -> {
            onlyIf {
                gradle.taskGraph.hasTask(prepareIntegrationTestRepository.get())
            }
        }

        isCandidatePublication || isIntegrationTestRepository -> {
            enabled = false
        }
    }
}

tasks.withType<org.gradle.api.publish.maven.tasks.PublishToMavenLocal>().configureEach {
    if (name.startsWith("publishIntegrationTest")) {
        enabled = false
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
        target = "https://maven.pkg.github.com/zucca-devops-tooling/gradle-publisher"
        usernameProperty = "githubPackagesUsername"
        passwordProperty = "githubPackagesPassword"
        sign = false
    }
    prod {
        target = "mavenCentral"
    }

    alterProjectVersion = false
    usernameProperty = "mavenCentralUsername"
    passwordProperty = "mavenCentralPassword"
    releaseBranchPatterns = listOf("^release/\\d+\\.\\d+\\.\\d+$", "^hotfix/\\d+\\.\\d+\\.\\d+$")
}

spotless {
    kotlin {
        target("src/main/**/*.kt", "src/functionalTest/**/*.kt", "src/integrationTest/**/*.kt")
        ktlint()
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
