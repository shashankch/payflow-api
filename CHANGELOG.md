# Changelog

All notable changes to the Payflow API project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Added
- Project scaffolding: MIT `LICENSE`, `CHANGELOG.md`, `.editorconfig`.
- Architectural Decision Records log (`docs/ADR.md`) and engineering standards guide (`docs/CONVENTIONS.md`).
- Spotless code formatting plugin (`com.diffplug.spotless:spotless-maven-plugin`) integrated into Maven build.
- Checkstyle static analysis (`checkstyle.xml`) plugin (`maven-checkstyle-plugin`) enforcing coding rules on `validate` phase.
- GitHub Actions CI pipeline (`.github/workflows/ci.yml`) for automated build, linting, formatting, and test execution on Java 25.
- Repository contribution guidelines and branch protection rules (`CONTRIBUTING.md`).
- GitHub status badges in `README.md`.

---

## [0.1.0] - 2026-07-27

### Added
- Baseline Phase 0 implementation.
- Basic User (`/users`) and Transaction (`/transactions`) REST endpoints.
- Spring Data JPA entities (`User`, `Transaction`) and repositories.
- In-memory H2 database persistence for local development builds.
- Initial Spring Boot 4.0.6 project configuration with Java 25.
- System Architecture documentation (`docs/ARCHITECTURE.md`), API Specification (`docs/API_SPECIFICATION.md`), and Phased Roadmap (`docs/ROADMAP.md`).
