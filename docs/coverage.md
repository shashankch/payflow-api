# 📊 Automated Code Coverage & Quality Metrics

[![Coverage](https://img.shields.io/badge/Coverage-90%25%20Line%20%7C%2073%25%20Branch-brightgreen?style=flat-square)](https://shashankch.github.io/payflow-api/coverage-report/)
[![Quality Gate](https://img.shields.io/badge/SpotBugs-Passed%20(0%20Bugs)-brightgreen?style=flat-square)](https://github.com/shashankch/payflow-api/actions/workflows/ci.yml)
[![Tests](https://img.shields.io/badge/Tests-191%20Passed%20%7C%200%20Failed-brightgreen?style=flat-square)](https://github.com/shashankch/payflow-api/actions/workflows/ci.yml)

The Payflow API continuous integration pipeline strictly enforces automated bytecode static analysis via **SpotBugs** and bundle-level code coverage thresholds via **JaCoCo** (`jacoco-maven-plugin:0.8.15`).

---

## 🎯 Coverage Quality Thresholds & Status

| Quality Dimension | Enforced Gate Threshold | Current Achieved Status | Status |
| :--- | :--- | :--- | :--- |
| **Line Coverage** | `80%` minimum | **90%** (across core business packages) | :white_check_mark: **PASSED** |
| **Branch Coverage** | `70%` minimum | **73%** (across complex conditional paths) | :white_check_mark: **PASSED** |
| **Unit & Integration Tests** | `100%` passing | **191 / 191 tests** (0 failures, 0 errors) | :white_check_mark: **PASSED** |
| **Missed Classes** | `0` missed | **0 missed classes** | :white_check_mark: **PASSED** |
| **SpotBugs Bytecode Audit** | Max Priority: Low (0 bugs) | **0 bugs, 0 errors** detected | :white_check_mark: **PASSED** |

---

## 🚀 Live Interactive JaCoCo Report

The complete, class-by-class interactive JaCoCo coverage report is automatically generated on every build and published alongside the documentation portal.

<p align="center" style="margin: 24px 0;">
  <a id="jacoco-direct-link" href="https://shashankch.github.io/payflow-api/coverage-report/" class="md-button md-button--primary" style="font-weight: 600; padding: 10px 24px;">
    📊 Launch Interactive JaCoCo Code Coverage Report &rarr;
  </a>
</p>

!!! note "Seamless Navigation"
    Every page within the interactive JaCoCo report features an integrated, persistent top navigation header allowing seamless single-click bidirectional navigation:

    - **← Back to Documentation Portal**: Returns directly to the root documentation homepage.
    - **📊 Coverage Quality Gates**: Returns directly to this Quality Gate & Thresholds summary page.

---

## 📦 Package Coverage Breakdown

Coverage enforcement is focused on operational correctness, concurrency invariants, and data integrity:

| Package | Key Architectural Responsibilities | Coverage Profile |
| :--- | :--- | :--- |
| `com.payflow.service` | `TransactionService`, `UserService`, `SpendInsightsService`, `RedissonDistributedLockService` | High Line & Branch coverage; all edge cases tested |
| `com.payflow.filter` | `IdempotencyFilter`, `RequestLoggingFilter`, request payload SHA-256 caching | High branch coverage for in-flight contention & key reuse |
| `com.payflow.security` | `JwtAuthenticationFilter`, `JwtTokenProvider`, `SecurityUtils`, RBAC | 100% path coverage for authentication & principal authorization |
| `com.payflow.resilience` | `PerUserRateLimiterAspect`, `UserRateLimiterService`, Circuit Breaker | Full verification of token bucket permissions & 429 translation |
| `com.payflow.controller` | `TransactionController`, `UserController`, `AuthController` | WebMvcTest verification of HTTP status codes & RFC 7807 payloads |
| `com.payflow.event` | `TransferCompletedEvent`, `TransferEventListener`, Kafka externalization | Verification of transactional outbox event lifecycle |

---

## 🛡️ Excluded Packages & Justification

Following industry best practices ([ADR-030](adr/0030-ci-cd-quality-gates-jacoco-spotbugs.md)), purely declarative components without executable logic are excluded from bundle-level coverage metrics to prevent artificial metric skew:

1. **Entities & Value Objects** (`com/payflow/entity/*`): Pure JPA entities with getters/setters/builders.
2. **Data Transfer Objects** (`com/payflow/dto/*`): Validation records and request/response payloads.
3. **Generated Mappers** (`com/payflow/mapper/*Impl*`): Compile-time generated MapStruct mapping bytecode.
4. **Spring Configuration Classes** (`com/payflow/config/*`): Declarative `@Bean` wiring definitions.

<script>
(function() {
  function adjustCoverageLink() {
    var link = document.getElementById("jacoco-direct-link");
    if (!link) return;
    var path = window.location.pathname;
    if (path.includes("/payflow-api/")) {
      link.href = "/payflow-api/coverage-report/";
    } else {
      link.href = "/coverage-report/";
    }
  }
  if (typeof document$ !== "undefined") {
    document$.subscribe(adjustCoverageLink);
  } else if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", adjustCoverageLink);
  } else {
    adjustCoverageLink();
  }
})();
</script>
