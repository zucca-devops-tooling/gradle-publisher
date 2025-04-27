# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]
### 🐞 Fixed
- Plugin configuration was incorrectly skipped when invoking `publish` for a specific module in a multi-project build (e.g., `./gradlew :module:publish`).
## [1.0.1] - 2025-04-26
### 🐞 Fixed
- Gradle incompatibility with nested objects in `publisher` extension

### ✨ Added
- Support for Git branch detection when using a new local branch that has not been pushed to origin.

### 🔄 Changed
- Improved minor logging details.

## [1.0.1] - 2025-04-22
### 🐞 Fixed
- Plugin no longer performs unnecessary publishing configuration when unrelated tasks (e.g., `publishPlugins`) are requested.
- Now evaluates publishing logic **only** when the exact `publish` task is included in the requested task graph.

## [1.0.0] - 2025-04-21
### ⚠️ Breaking Change
- `alterProjectVersion` no longer prevents `project.version` from being updated during the `publish` task.
    - Ensures consistent versioning for all publications, including plugin marker publications.

### ✨ Added
- `publisher.resolvedVersion`: Always provides the computed version (e.g., `1.0.0-feature-branch-SNAPSHOT`), ignoring the `alterProjectVersion` setting.
- `publisher.effectiveVersion`: Provides the computed version respecting the `alterProjectVersion` setting.

### 🔄 Changed
- Plugin publishing configuration is now executed **only** when explicitly invoking the `publish` task (`./gradlew publish`).
    - Prevents unintended side effects during unrelated tasks (`build`, `test`, etc.).
- Version computation continues during all Gradle tasks but applies changes **only when required** (respects `alterProjectVersion`).

### 🎉 Highlights
- Stabilized and finalized publishing workflow.

## [0.1.1] - 2025-04-20
### ✨ Added
- Configuration validation to prevent the plugin from running in an invalid or incomplete state

## [0.1.0] - 2025-04-19
### ✨ Added
- New configuration option: `alterProjectVersion` which when set to false prevents the plugin to modify the project version

### 🔄 Changed
- Avoid unnecessary third-party plugin application and task execution on non-publishable versions

## [0.0.2] - 2025-04-19
### ✨ Added
- Logging across all major plugin operations (e.g. version resolution, repository setup, task routing)
- Logging for minor configuration details and step-by-step processing
- Internal efficiency improvements through caching and memoization

## [0.0.1] - 2025-04-16
### ✨ Added
- Initial release with Git Flow setup.
- CHANGELOG.md added.

[Unreleased]: https://github.com/zucca-devops-tooling/gradle-publisher/compare/v1.0.2...HEAD
[1.0.2]: https://github.com/zucca-devops-tooling/gradle-publisher/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/zucca-devops-tooling/gradle-publisher/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/zucca-devops-tooling/gradle-publisher/compare/v0.1.1...v1.0.0
[0.1.1]: https://github.com/zucca-devops-tooling/gradle-publisher/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/zucca-devops-tooling/gradle-publisher/compare/v0.0.2...v0.1.0
[0.0.2]: https://github.com/zucca-devops-tooling/gradle-publisher/compare/v0.0.1...v0.0.2
[0.0.1]: https://github.com/zucca-devops-tooling/gradle-publisher/releases/tag/v0.0.1