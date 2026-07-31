# Changelog

All notable changes to the Payflow API project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Added
- Phase 2A Entity Model Hardening & Rich Domain:
  - Replaced `Double` primitives with `BigDecimal` (`precision = 19, scale = 4`) across `User` and `Transaction` entities.
  - Implemented Rich Domain methods (`User.debit()`, `User.credit()`) encapsulating balance invariants and state validation.
  - Added audit timestamps (`createdAt`, `updatedAt`) and optimistic locking (`@Version version`) support to `User`.
  - Added `TransactionStatus` (`INITIATED`, `COMPLETED`, `FAILED`, `REFUNDED`) and `TransactionType` (`TRANSFER`, `REFUND`) enums.
  - Added JPA `@ManyToOne` foreign key relationships between `Transaction` and `User` entities with denormalized UPI strings.
  - Added UUID `referenceId` auto-generation (`@PrePersist`) on `Transaction`.
  - Refactored all services (`UserService`, `TransactionService`) and controllers (`UserController`, `TransactionController`) to use constructor injection.
  - Unit test suite for `User` domain logic and `Transaction` reference ID auto-generation (`UserTest`, `TransactionTest`).
  - Architectural Decision Records: `ADR-001` (BigDecimal), `ADR-002` (Constructor injection), `ADR-003` (Rich Domain Model).
- Project scaffolding: MIT `LICENSE`, `CHANGELOG.md`, `.editorconfig`.
- Architectural Decision Records log (`docs/ADR.md`) and engineering standards guide (`docs/CONVENTIONS.md`).
- Spotless code formatting plugin (`com.diffplug.spotless:spotless-maven-plugin`) integrated into Maven build.
- Checkstyle static analysis (`checkstyle.xml`) plugin (`maven-checkstyle-plugin`) enforcing coding rules on `validate` phase.
- GitHub Actions CI pipeline (`.github/workflows/ci.yml`) for automated build, linting, formatting, and test execution on Java 25.
- Repository contribution guidelines and branch protection rules (`CONTRIBUTING.md`).
- GitHub status badges in `README.md`.

### Fixed
- Dockerfile JDK version mismatch: `eclipse-temurin:21-jre` → `eclipse-temurin:25-jre` to align with project's `java.version`.
- Broken GraalVM native profile in `pom.xml`: removed invalid dependency declaration with undefined property reference.
- Removed `System.out.println` debug statement from `UserController`.
- README updated to accurately reflect current project status.

---

## [0.1.0] - 2026-07-27

### Added
- Baseline Phase 0 implementation.
- Basic User (`/users`) and Transaction (`/transactions`) REST endpoints.
- Spring Data JPA entities (`User`, `Transaction`) and repositories.
- In-memory H2 database persistence for local development builds.
- Initial Spring Boot 4.0.6 project configuration with Java 25.
- System Architecture documentation (`docs/ARCHITECTURE.md`), API Specification (`docs/API_SPECIFICATION.md`), and Phased Roadmap (`docs/ROADMAP.md`).
