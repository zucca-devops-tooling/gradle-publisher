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
- 🧾 Automatic publishing extension configuration

---

## 🚀 Quick Start

Apply the plugin:

```kotlin
plugins {
  id("dev.zucca-ops.gradle-publisher") version "0.1.1"
}
```

Minimal configuration (defaults `target` to `local` if omitted):

```kotlin
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

### Skipping GPG Signing

Disable GPG signing for environments where signing isn't required, typically dev or local:

```kotlin
dev {
    target = "dev-repo-url"
    sign = false
}
```

### Custom POM Configuration

Customizing the POM is necessary when publishing to repositories like Maven Central:

```kotlin
publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("...")
            description.set("...")
            url.set("...")
            licenses { /*...*/ }
            developers { /*...*/ }
            scm { /*...*/ }
        }
    }
}
```

### Altering Project Version

To prevent the plugin from modifying `project.version` directly, set `alterProjectVersion` to `false`:

```kotlin
publisher {
    alterProjectVersion = false
}
```

This allows the plugin to compute a version internally without altering the project's version, useful for `afterEvaluate` scenarios.

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

