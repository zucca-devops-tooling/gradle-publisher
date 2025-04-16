# Gradle Publisher Plugin

The **Gradle Publisher Plugin** automates CI-based publishing of Gradle artifacts. It manages versioning and intelligently routes artifacts to the appropriate repository based on the Git branch and environment.

---

## ✨ Features

- 🔀 Automatically changes version names depending on your Git branch
- 📦 Supports separate dev and prod repository targets
- 🔑 Environment-specific credentials
- 🔏 Optional GPG signing (with skip control per environment)
- 🧠 Smart routing for Nexus or Maven Central publishing
- 🧰 Automatically applies and configures `maven-publish`
- 🧾 Automatically configures publishing extension

---

## 🚀 Usage

### 1. Apply the Plugin

```kotlin
plugins {
    id("dev.zucca-ops.gradle-publisher") version "0.0.1"
}
```

### 2. Configure the Publisher

```kotlin
publisher {
    dev {
        target = "https://zucca.jfrog.io/artifactory/publisher-libs-snapshot"
        usernameProperty = "devUserProperty"
        passwordProperty = "devPasswordProperty"
        sign = false // optional: disable signing in dev
    }
    prod {
        target = "https://zucca.jfrog.io/artifactory/publisher-libs-release"
        usernameProperty = "prodUserProperty"
        passwordProperty = "prodPasswordProperty"
    }

    gitFolder = "some/path/to/.git" // defaulted to "." 

    releaseBranchPatterns = ["^release/\d+\.\d+\.\d+$", "^v\d+\.\d+\.\d+$"] // optional, default to main branch
}
```

### 3. Version Behavior

If the current Git branch **does not match** any `releaseBranchPatterns`, the plugin modifies your version:

```
project.version = 1.5.3 → 1.5.3-<branch-name>-SNAPSHOT
```

If the branch matches a release pattern, the version is kept as-is.

If `releaseBranchPatterns` is not defined, the plugin will attempt to detect the default branch (e.g., `main`, `master`) from Git configuration.

---

## 🔐 Global Credentials Example

You can also define credentials globally:

```kotlin
publisher {
    dev {
        target = "https://zucca.jfrog.io/artifactory/publisher-libs-snapshot"
    }
    prod {
        target = "https://zucca.jfrog.io/artifactory/publisher-libs-release"
    }

    usernameProperty = "userProperty"
    passwordProperty = "passwordProperty"
    releaseBranchPatterns = ["^release/\d+\.\d+\.\d+$", "^v\d+\.\d+$"]
}
```

---

## 🧪 Special Cases

### ➕ Publishing to Nexus

For **Sonatype OSSRH/Nexus**, set:

```kotlin
prod {
    target = "nexus"
    usernameProperty = "ossrhUserProperty"
    passwordProperty = "ossrhPassProperty"
    customGradleCommand = "closeAndReleaseStagingRepositories"
}
```

⚠️ You must manually apply and configure the [Nexus Publish Plugin](https://github.com/gradle-nexus/publish-plugin).

You can define any custom publishing task that will be triggered when running:

```bash
./gradlew publish
```

### ☁️ Publishing to Maven Central (New Portal)

Set the target to:

```kotlin
prod {
    target = "mavenCentral"
}
```

This uses [flying-gradle-plugin](https://github.com/yananhub/flying-gradle-plugin) under the hood.

By default, this sets:

```kotlin
customGradleCommand = "publishToMavenCentralPortal"
```

You may override it with your own custom task.

---

## 📦 Local Publishing

To publish to your local `.m2` repository (e.g. for testing):

```kotlin
dev {
    target = "local"
}
```

This will skip signing and publish directly to `~/.m2/repository`.
Signing is automatically disabled in this mode.

---

## 🔏 Skipping GPG Signing

If you have signing configured but want to **skip it in a specific environment**, simply add:

```kotlin
sign = false
```

---

## 🔧 Custom POM Configuration

If you need a **custom POM**, configure it like this:

```kotlin
publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("...")
            description.set("...")
            url.set("...")

            licenses {
                license {
                    name.set("...")
                    url.set("...")
                    distribution.set("...")
                }
            }

            developers {
                developer {
                    id.set("...")
                    name.set("...")
                    email.set("...")
                }
            }

            scm {
                url.set("...")
                connection.set("...")
                developerConnection.set("...")
            }
        }
    }
}
```

---

## 📌 Notes

- `maven-publish` is automatically applied for you
- No need to manually configure `MavenPublication` unless customizing
- Use `customGradleCommand` to override the publish task per environment
- Compatible with CI/CD systems like Jenkins, GitHub Actions, GitLab CI, etc.

---

## 📎 Example CLI

```bash
./gradlew publish
```

Let the plugin handle the logic — based on your current branch, it will:

- Route to dev or prod
- Apply the correct version suffix
- Use the right credentials
- Execute the correct publish command