import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

val integrationTestCandidateVersion = "0.0.0-integration-test"
val integrationTestRepositoryDirectory = layout.buildDirectory.dir("integration-test-repository")
val integrationTestConsumerDirectory = layout.buildDirectory.dir("integration-test-consumers")

val sourceSets = extensions.getByType<SourceSetContainer>()
val integrationTestSourceSet = sourceSets.create("integrationTest")

configurations[integrationTestSourceSet.implementationConfigurationName]
    .extendsFrom(configurations.getByName("testImplementation"))
configurations[integrationTestSourceSet.runtimeOnlyConfigurationName]
    .extendsFrom(configurations.getByName("testRuntimeOnly"))

dependencies.add(
    integrationTestSourceSet.implementationConfigurationName,
    dependencies.gradleTestKit(),
)

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

extensions.configure<PublishingExtension> {
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
}

val candidatePublicationTasks =
    listOf(
        "publishIntegrationTestPluginMavenPublicationToIntegrationTestRepository",
        "publishIntegrationTestPluginMarkerMavenPublicationToIntegrationTestRepository",
    )

tasks.matching { it.name in candidatePublicationTasks }.configureEach {
    mustRunAfter(cleanIntegrationTestRepository)
}

val prepareIntegrationTestRepository =
    tasks.register("prepareIntegrationTestRepository") {
        description = "Publishes the packaged candidate plugin into an isolated Maven repository."
        group = LifecycleBasePlugin.BUILD_GROUP
        dependsOn(cleanIntegrationTestRepository, candidatePublicationTasks)
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
        shouldRunAfter(tasks.named("functionalTest"))
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

tasks.named("check") {
    dependsOn(integrationTest)
}

tasks.withType<PublishToMavenRepository>().configureEach {
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

tasks.withType<PublishToMavenLocal>().configureEach {
    if (name.startsWith("publishIntegrationTest")) {
        enabled = false
    }
}
