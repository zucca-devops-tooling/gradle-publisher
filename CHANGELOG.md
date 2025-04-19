# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.1.0] - 2025-04-19
### Added
- New configuration option: `alterProjectVersion` which when set to false prevents the plugin to modify the project version

### Changed
- Avoid unnecessary third-party plugin application and task execution on non-publishable versions

## [0.0.2] - 2025-04-19
### Added
- Logging across all major plugin operations (e.g. version resolution, repository setup, task routing)
- Logging for minor configuration details and step-by-step processing
- Internal efficiency improvements through caching and memoization

## [0.0.1] - 2025-04-16
### Added
- Initial release with Git Flow setup.
- CHANGELOG.md added.

[Unreleased]: https://github.com/zucca-devops-tooling/gradle-publisher/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/zucca-devops-tooling/gradle-publisher/compare/v0.0.2...v0.1.0
[0.0.2]: https://github.com/zucca-devops-tooling/gradle-publisher/compare/v0.0.1...v0.0.2
[0.0.1]: https://github.com/zucca-devops-tooling/gradle-publisher/releases/tag/v0.0.1