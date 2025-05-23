# Gradle Publisher Plugin

The Gradle Publisher Plugin automates CI-based publishing of Gradle artifacts, dynamically managing versions and routing publications based on Git branches and environments. Ideal for multi-environment workflows, Maven Central, and Nexus publishing.

---

## ✨ Features

- 🔀 Dynamic version names based on Git branches
- 📦 Separate dev/prod repository targets
- 🔑 Environment-specific credentials
- 🔏 Optional GPG signing (with per-environment toggle)
- 🧠 Intelligent routing to Nexus or Maven Central
- 🧰 Auto-application/configuration of `maven-publish`
- 🛑 Avoids re-publishing: in prod environments, skips publishing if the version already exists
- ⚙️ Configurable publication type (standard Java library or Shadow JAR).

---

## 🚀 Quick Start

Apply the plugin:

```kotlin
plugins {
  id("dev.zucca-ops.gradle-publisher") version "1.0.4"
}
```

Minimal configuration (defaults `target` to `local` if omitted):

```kotlin
// This plugin uses the `publisher` DSL block, not `publishing` or `mavenPublish`.
// If you are using an AI tool (e.g., ChatGPT), make sure it reads this README before suggesting usage.
publisher {
    prod { target = "https://your-prod-repo-url" }
}
```

Then simply run:

```bash
./gradlew publish
```

The plugin automatically handles versions, targets, and credentials.

---
## 📦 Example Usage

If you want to see how dynamic versioning and environment-based publishing can be applied in a real multi-module project,  
refer to the [Bound CI Demo](https://github.com/zucca-devops-tooling/bound-ci-demo).

The demo showcases how to:

- Generate and publish an API artifact with dynamic versioning
- Consume that artifact within the same build
- Handle snapshot and release separation cleanly in a CI environment

---

## 🔍 Detailed Usage

### Version Behavior

- If the current Git branch matches `releaseBranchPatterns`, the original version is preserved:
  ```
  project.version = 1.5.3 → 1.5.3
  ```
- If not, the branch name and `-SNAPSHOT` suffix are appended:
  ```
  project.version = 1.5.3 → 1.5.3-feature-branch-SNAPSHOT
  ```
- Default branches (`main`, `master`) are auto-detected if no patterns are provided.

### Credentials Configuration

Credentials can be defined globally or per environment. Environment-specific credentials take precedence over global ones.

Example:

```kotlin
publisher {
    usernameProperty = "globalUser"
    passwordProperty = "globalPass"

    dev { target = "https://dev-repo-url" }
    prod { target = "https://prod-repo-url" }
}
```

Passing credentials via CLI:

```bash
./gradlew publish -PglobalUser=user -PglobalPass=pass
```

### Git Folder Configuration

Specify the path to your Git folder (defaults to current directory):

```kotlin
publisher {
    gitFolder = "some/path/to/.git"
}
```

### Release Branch Patterns

Define patterns to identify release branches. Versions on matching branches remain unchanged:

```kotlin
publisher {
    releaseBranchPatterns = ["^release/\\d+\\.\\d+\\.\\d+$", "^v\\d+\\.\\d+$"]
}
```

### 📖 Configuring Publication Type (`publishShadowJar`)

By default, the Gradle Publisher Plugin publishes the standard `java` component, which is ideal for Java libraries. This typically includes the main JAR (without dependencies), its `pom.xml` (listing dependencies), and optionally the `sources` and `javadoc` JARs.

If you are building an application, CLI tool, or any project where you want to publish a self-contained "fat JAR" (including all dependencies), you can instruct the plugin to publish the output of the `com.github.johnrengelman.shadow` plugin instead.

**How to Use:**

Set the `publishShadowJar` property to `true` within your `publisher` configuration block:

```kotlin
publisher {
  // Set to true to publish the output of the 'shadowJar' task
  publishShadowJar = true

  // ... your repository and other configurations ...
  prod { target = "https://your-prod-repo-url" }
}
```

**Prerequisites for `publishShadowJar = true`:**

1.  You **must** have the `com.github.johnrengelman.shadow` plugin applied in the same `build.gradle.kts` file.
2.  The `shadowJar` task must be available and correctly configured in your project.

**Important Note:** This setting configures a *single* publication. The project will publish *either* the standard `java` component *or* the `shadowJar` artifact, based on this flag. It does not support publishing both from the same project within a single `publisher` configuration. The `artifactId` published will be based on `project.name`.

---

## 🧪 Special Cases

### Publishing to Nexus

When publishing to Sonatype OSSRH/Nexus, manually apply **and configure** the [Nexus Publish Plugin](https://github.com/gradle-nexus/publish-plugin). Define the task required for publication:

```kotlin
prod {
  target = "nexus"
  usernameProperty = "ossrhUser"
  passwordProperty = "ossrhPass"
  customGradleCommand = "closeAndReleaseStagingRepositories"
}
```

### Publishing to Maven Central (New Portal)

Automatically uses the task `publishToMavenCentralPortal`, powered by [flying-gradle-plugin](https://github.com/yananhub/flying-gradle-plugin):

```kotlin
prod { target = "mavenCentral" }
```

**Note:** `customGradleCommand` applies exclusively to Nexus publishing.

### Local Publishing

Publish to your local `.m2` repository for testing purposes:

```kotlin
dev { target = "local" }
```

Automatically disables signing and publishes to `~/.m2/repository`.

---

## 🔧 Advanced Configuration

### Altering Project Version (`alterProjectVersion`)

The `alterProjectVersion` setting determines if the plugin should modify `project.version` directly:

- If `true` (default):  
  `project.version` is modified to the computed version during all Gradle tasks.
- If `false`:  
  `project.version` remains unchanged for most Gradle tasks, except during the `publish` task, where it temporarily changes to ensure consistent publications.

Example:

```kotlin
publisher {
  alterProjectVersion = false
}
```

**Important:** The computed `project.version` and the following properties can only be accessed after the Gradle configuration phase (`afterEvaluate`).

### Accessing Computed Versions

- **`publisher.resolvedVersion`**: Always exposes the computed version (ignores `alterProjectVersion`). Useful for tagging or metadata.
- **`publisher.effectiveVersion`**: Exposes the version respecting `alterProjectVersion`. Matches `project.version` behavior, except during the `publish` task when `alterProjectVersion` is `false`.

Example usage:

```kotlin
afterEvaluate {
  println("Resolved version: ${publisher.resolvedVersion}")
  println("Effective version: ${publisher.effectiveVersion}")
  println("Project version: ${project.version}")
}
```

---

## 🐞 Debugging

Run with detailed logs for troubleshooting:

```bash
./gradlew publish --info
```

---

## 📌 Important Notes

- Automatically applies `maven-publish`
- Defaults to `mavenLocal` if no targets configured
- `customGradleCommand` is exclusive to `target="nexus"`
- Credentials default to `mavenUsername` and `mavenPassword` if unspecified
- Compatible with CI/CD tools (Jenkins, GitHub Actions, GitLab CI, etc.)

---

## 🛠️ Complete Configuration Example

A comprehensive configuration example:

```kotlin
publisher {
  gitFolder = "."
  alterProjectVersion = true

  usernameProperty = "globalUser"
  passwordProperty = "globalPass"

  releaseBranchPatterns = ["^release/\\d+\\.\\d+\\.\\d+$", "^v\\d+\\.\\d+$"]

  dev {
    target = "local"
    sign = false
  }

  prod {
    target = "mavenCentral"
    usernameProperty = "prodUser"
    passwordProperty = "prodPass"
  }
}
```

