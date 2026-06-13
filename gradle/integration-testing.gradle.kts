import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.Sign

val integrationTestCandidateVersion = "0.0.0-integration-test"
val integrationTestRepositoryDirectory = layout.buildDirectory.dir("integration-test-repository")
val integrationTestConsumerDirectory = layout.buildDirectory.dir("integration-test-consumers")
val integrationTestEntryTasks =
    setOf(
        "integrationTest",
        "prepareIntegrationTestRepository",
        "check",
        "build",
        "buildNeeded",
        "buildDependents",
    )
val integrationTestRequested =
    gradle.startParameter.taskNames.any {
        it.substringAfterLast(':') in integrationTestEntryTasks
    }

if (integrationTestRequested) {
    // Gradle's generated implementation and marker publications inherit this candidate version.
    project.version = integrationTestCandidateVersion
}

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
    repositories {
        maven {
            name = "integrationTest"
            url = integrationTestRepositoryDirectory.get().asFile.toURI()
        }
    }
}

val candidatePublicationTasks =
    listOf(
        "publishPluginMavenPublicationToIntegrationTestRepository",
        "publishGradlePublisherPluginPluginMarkerMavenPublicationToIntegrationTestRepository",
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

if (integrationTestRequested) {
    gradle.taskGraph.whenReady {
        listOf(
            "signPluginMavenPublication",
            "signGradlePublisherPluginPluginMarkerMavenPublication",
        ).forEach { taskName ->
            val signTask = tasks.getByName(taskName) as Sign
            signTask.apply {
                required(false)
                enabled = false
            }
        }
    }
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
    val isIntegrationTestRepository = name.endsWith("ToIntegrationTestRepository")

    if (isIntegrationTestRepository) {
        if (name in candidatePublicationTasks) {
            onlyIf {
                integrationTestRequested
            }
        } else {
            enabled = false
        }
    }
}
