# ADR-014: Spring Environment Profiles and Testcontainers Integration Testing Strategy

* **Date**: 2026-08-11
* **Status**: Accepted
* **Phase**: Phase 4B

## Context & Problem Statement
Running application integration tests against in-memory H2 databases can mask database-specific SQL dialect bugs, locking behavior differences, and Flyway migration syntax errors that only manifest in production PostgreSQL environments. Conversely, requiring a manually managed external PostgreSQL database for every unit test run causes developer friction and fragile CI builds.

## Considered Options
1. **Single H2 Environment Configuration**: Simple but lacks production environment parity and risks PostgreSQL dialect/locking incompatibilities.
2. **Environment Profiles (`local`, `test`, `prod`) with Testcontainers PostgreSQL**:
   - `local`: Embedded H2 in PostgreSQL compatibility mode for instant zero-dependency local development (`application-local.yml`).
   - `test`: Real PostgreSQL container booted on-demand via Testcontainers (`@Testcontainers`, `@DynamicPropertySource`) for integration tests (`application-test.yml`).
   - `prod`: External PostgreSQL database configured via environment variables with HikariCP connection pool tuning and graceful shutdown (`application-prod.yml`).

## Decision Outcome
Chosen Option: **Environment Profiles (`local`, `test`, `prod`) with Testcontainers PostgreSQL**

### Rationale
* **Production Parity in Tests**: Testcontainers boots a real Dockerized PostgreSQL container during integration test execution, validating Flyway migrations (`V1`..`V4`), dialect behavior, and JPA locking against real PostgreSQL.
* **Instant Local Dev Iteration**: The `local` profile uses H2 in PostgreSQL mode, enabling instant application startup without local Docker overhead.
* **Graceful Execution**: Using `@Testcontainers(disabledWithoutDocker = true)` ensures tests run automatically when Docker is active, while falling back gracefully when running in Docker-less environments.

## Consequences
* **Positive**: 100% production parity for database integration tests, zero environment drift, clean separation of configuration per profile.
* **Negative / Trade-offs**: Integration test execution requires Docker daemon access when running Testcontainers tests.
