# Gradle Publisher Plugin

`gradle-publisher` is a Gradle plugin for CI-oriented publishing.

It resolves a project version from Git state, chooses a publishing target based on whether the current branch is treated as `dev` or `prod`, and wires repository credentials from Gradle properties.

It is intended for builds that need one publishing flow with:

- dynamic snapshot versions for non-release branches
- stable versions for release/default branches
- separate dev/prod repositories
- optional signing control per target
- support for local, remote, Nexus, and Maven Central publishing

## What It Does

The plugin:

- applies `maven-publish`
- computes a publish version from the current Git branch
- treats release branches as `prod`
- treats all other branches as `dev`
- picks the configured repository for the active environment
- resolves credentials from explicit environment config, then falls back to global config
- skips re-publishing when the artifact already exists for release publishing targets

## Quick Start

Apply the plugin:

```kotlin
plugins {
    id("dev.zucca-ops.gradle-publisher") version "1.1.0"
}
```

Minimal configuration:

```kotlin
publisher {
    prod {
        target = "https://your-prod-repo-url"
    }
}
```

Then publish:

```bash
./gradlew publish
```

If no target is configured, the plugin defaults to `local`.

## Versioning Rules

Given `project.version = 1.5.3`:

- on a release/default branch: `1.5.3`
- on a non-release branch like `feature/my-work`: `1.5.3-feature-my-work-SNAPSHOT`

Release detection works like this:

- if `releaseBranchPatterns` is configured, the current branch must match one of those regexes
- otherwise, the plugin treats the repository default branch as the release branch
- if the default branch cannot be detected, it falls back to `main` / `master`

## Configuration Reference

Top-level `publisher` properties:

| Property | Type | Default | Description |
| --- | --- | --- | --- |
| `usernameProperty` | `String` | `mavenUsername` | Global fallback username property |
| `passwordProperty` | `String` | `mavenPassword` | Global fallback password property |
| `gitFolder` | `String` | `"."` | Repository root or `.git` entry used for Git resolution |
| `releaseBranchPatterns` | `List<String>` | `[]` | Regexes that define release branches |
| `alterProjectVersion` | `Boolean` | `true` | Whether the plugin updates `project.version` outside publish execution |
| `shadowJar` | `Boolean` | `false` | Publish the `shadowJar` task output instead of the standard `java` component |

Per-environment properties (`dev {}` / `prod {}`):

| Property | Type | Default | Description |
| --- | --- | --- | --- |
| `target` | `String` | `local` | Publishing target |
| `usernameProperty` | `String` | `mavenUsername` | Username property for this environment |
| `passwordProperty` | `String` | `mavenPassword` | Password/token property for this environment |
| `customGradleCommand` | `String?` | `null` | Required when `target = "nexus"` |
| `sign` | `Boolean` | `true` | Whether artifacts should be signed for this environment |

Credential lookup order:

1. environment-specific property name
2. global `publisher.usernameProperty` / `publisher.passwordProperty`
3. built-in defaults `mavenUsername` / `mavenPassword`

## Supported Targets

### Local

```kotlin
publisher {
    dev {
        target = "local"
        sign = false
    }
}
```

Publishes to `~/.m2/repository`.

### Remote Maven Repository

```kotlin
publisher {
    dev {
        target = "https://your-dev-repo-url"
        usernameProperty = "devUser"
        passwordProperty = "devPassword"
        sign = false
    }

    prod {
        target = "https://your-prod-repo-url"
        usernameProperty = "prodUser"
        passwordProperty = "prodPassword"
    }
}
```

### Maven Central Portal

```kotlin
publisher {
    prod {
        target = "mavenCentral"
    }
}
```

This routes `publish` to `publishToMavenCentralPortal`.

### Nexus / OSSRH

```kotlin
publisher {
    prod {
        target = "nexus"
        usernameProperty = "ossrhUser"
        passwordProperty = "ossrhPass"
        customGradleCommand = "closeAndReleaseStagingRepositories"
    }
}
```

For `nexus`, you must apply and configure the Nexus publishing plugin yourself. `customGradleCommand` is only used for `target = "nexus"`.

## Credentials

Pass credentials as Gradle properties, for example:

```bash
./gradlew publish -PprodUser=user -PprodPassword=token
```

Or configure global fallbacks:

```kotlin
publisher {
    usernameProperty = "globalUser"
    passwordProperty = "globalPass"

    dev {
        target = "https://dev-repo-url"
    }

    prod {
        target = "https://prod-repo-url"
    }
}
```

## Git Configuration

By default the plugin resolves Git metadata from the current project directory.

If your build runs from a different path, point it at the repository root or the `.git` entry:

```kotlin
publisher {
    gitFolder = "some/path/to/.git"
}
```

If the configured path is not Git-aware, the plugin fails fast with a detailed error instead of guessing.

## Shadow JAR Publishing

To publish a `shadowJar` artifact instead of the normal `java` component:

```kotlin
publisher {
    shadowJar = true

    prod {
        target = "https://your-prod-repo-url"
    }
}
```

Requirements:

- a `shadowJar` task must exist in the project
- this setting publishes one artifact variant, not both standard and shadow artifacts

## Accessing Resolved Versions

After evaluation, the plugin exposes:

- `publisher.resolvedVersion`
- `publisher.effectiveVersion`

Example:

```kotlin
afterEvaluate {
    println("Resolved version: ${publisher.resolvedVersion}")
    println("Effective version: ${publisher.effectiveVersion}")
    println("Project version: ${project.version}")
}
```

## Complete Example

```kotlin
publisher {
    gitFolder = "."
    alterProjectVersion = true

    usernameProperty = "globalUser"
    passwordProperty = "globalPass"

    releaseBranchPatterns = listOf(
        "^release/\\d+\\.\\d+\\.\\d+$",
        "^v\\d+\\.\\d+$",
    )

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

## Troubleshooting

Run with info logs:

```bash
./gradlew publish --info
```

Common points:

- `publish` is the task this plugin hooks into
- `publishPlugins` is separate from this plugin's publishing flow
- `alterProjectVersion = false` keeps the normal project version for most tasks and only applies the computed version during publishing
- if no release patterns are configured, release detection depends on default-branch resolution

## Example Project

For a multi-module example, see [Bound CI Demo](https://github.com/zucca-devops-tooling/bound-ci-demo).
