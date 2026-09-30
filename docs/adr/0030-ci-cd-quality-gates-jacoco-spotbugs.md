# ADR-030: Automated CI/CD Quality Gates, JaCoCo Coverage Enforcement, and SpotBugs Static Analysis

* **Date**: 2026-09-29
* **Status**: Accepted
* **Phase**: Phase 11C

## Context & Problem Statement

Maintaining the structural integrity, security, and correctness of an enterprise transactional banking system requires strict, automated quality gates integrated into the developer feedback loop and CI/CD pipelines.

Without enforced quality gates, regression bugs, unhandled null references, concurrency hazards, and code coverage decay easily slip through peer reviews. Specifically, the engineering team required:
1. Automated static code analysis detecting bug patterns, serialization risks, and security flaws at build time.
2. Automated code coverage enforcement requiring high thresholds on domain and transactional logic before code can be merged into `main`.
3. Automated artifact archiving allowing developers to inspect coverage reports directly within pull requests.

## Considered Options

### Option A: Manual Code Review and Spot Auditing
- **Pros**: Zero initial configuration overhead.
- **Cons**: High cognitive load on reviewers; inconsistent enforcement; vulnerable to human oversight on edge cases.

### Option B: External SaaS Quality Platforms (e.g., SonarQube Cloud)
- **Pros**: Rich web UI and historical metrics tracking.
- **Cons**: Adds external SaaS dependency, API token management, and network latency to CI builds; potential enterprise firewall constraints.

### Option C: Native Maven Verification Pipeline with SpotBugs and JaCoCo Plugins (Chosen)
- **Pros**:
  - Zero external SaaS dependencies: Everything runs natively within the standard `mvn clean verify` lifecycle.
  - SpotBugs Static Analysis (`spotbugs-maven-plugin:4.10.4.1`): Audits Java 25 bytecode for null-pointer paths, unclosed resources, mutable exposures, and constructor leak vulnerabilities. Filtered via `spotbugs-exclude.xml` for generated MapStruct mappers and compatibility stubs.
  - JaCoCo Coverage Enforcement (`jacoco-maven-plugin:0.8.15`): Enforces minimum **80% line coverage** and **70% branch coverage** across core domain, service, and filter logic, while excluding pure data transfer objects (DTOs), configuration classes, and entities.
  - Hardened GitHub Actions CI: Automatically executes spotless formatting verification, checkstyle linting, unit tests, integration tests, SpotBugs analysis, and JaCoCo coverage validation, uploading report artifacts for 14-day retention.
- **Cons**: Build times increase slightly (~3-5s for SpotBugs and JaCoCo analysis).

## Decision Outcome

We adopted **Option C**. The quality enforcement system consists of:

1. **SpotBugs Maven Plugin Integration**:
   - Integrated `com.github.spotbugs:spotbugs-maven-plugin:4.10.4.1` with `effort: Max` and `threshold: Medium`.
   - Bound to the Maven `verify` lifecycle phase with `failOnError: true`.
   - Created `spotbugs-exclude.xml` to manage false positives on generated MapStruct `*Impl` classes and intentional architectural patterns.
   - Refactored core components (`JwtTokenProvider`, `Transaction`, `SecurityUtils`, and `RequestIdFilter`) to resolve constructor leaks (`CT_CONSTRUCTOR_THROW`), serialization warnings (`SE_BAD_FIELD`), and header validation sanitization.

2. **JaCoCo Coverage Enforcement**:
   - Integrated `org.jacoco:jacoco-maven-plugin:0.8.15` configured with Java 25 bytecode support.
   - Preserved agent instrumentation in Surefire and Failsafe using `@{argLine}`.
   - Configured bundle-level limits:
     - `LINE`: minimum `0.80` (80%)
     - `BRANCH`: minimum `0.70` (70%)
   - Excluded pure boilerplate packages (`dto`, `config`, `entity`, `mapper/*Impl.class`) to focus quality metrics on business algorithms, state machines, and security filters.
   - Current verification results: **90% line coverage**, **73% branch coverage**, and 0 missed classes.

3. **Hardened GitHub Actions CI Pipeline**:
   - Updated `.github/workflows/ci.yml` to run `mvn clean verify -B`.
   - Added automated GitHub Pages deployment for Docsify public documentation portal (`https://shashankch.github.io/payflow-api/`) and live interactive JaCoCo coverage reports at `https://shashankch.github.io/payflow-api/coverage/`.
   - Added code coverage status badge to `README.md` linking directly to the live GitHub Pages report.

## Consequences

### Positive
- **Guaranteed Coverage Baseline**: Pull requests failing to meet the 80% line or 70% branch threshold are automatically blocked by CI.
- **Proactive Bug Prevention**: SpotBugs detects concurrency, null-pointer, and resource leaks during local compilation before committing.
- **Standardized Developer Workflow**: Running `mvn clean verify` locally produces identical validation results to the GitHub Actions CI environment.

### Trade-offs & Mitigations
- **Branch Coverage Sensitivity**: Writing idiomatic Java with complex branching requires corresponding unit test scenarios. We maintain dedicated unit test suites (`JwtAuthenticationFilterTest`, `IdempotencyFilterTest`, `UserControllerTest`, `SecurityUtilsTest`) covering all conditional branches.
