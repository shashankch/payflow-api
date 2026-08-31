# ADR-013: Flyway Database Migrations Over DDL Auto-Generation

* **Date**: 2026-08-10
* **Status**: Accepted
* **Phase**: Phase 4A

## Context & Problem Statement
Relying on Hibernate `spring.jpa.hibernate.ddl-auto=update` or `create-drop` in production environments creates high-risk database mutation vulnerabilities: schema changes are unversioned, non-repeatable, cannot be reviewed in pull requests, and risk accidental data loss or locking during application restarts.

## Considered Options
1. **Hibernate DDL Auto-Generation (`ddl-auto=update`)**: Convenient for early prototyping, but non-deterministic, unversioned, and prohibited in production environments.
2. **Flyway Versioned SQL Migrations**: Versioned, immutable SQL migration scripts (`V1__...`, `V2__...`) stored in version control (`db/migration`), executed deterministically on startup, with Hibernate configured to `ddl-auto=validate`.

## Decision Outcome
Chosen Option: **Flyway Versioned SQL Migrations (`org.flywaydb:flyway-core`)**

### Rationale
* **Production Safety**: Schema migrations are explicit, version-controlled SQL files that can be audited in code reviews before deployment.
* **Strict Validation**: Hibernate `ddl-auto=validate` verifies entity mappings against Flyway-managed schema without altering database tables dynamically.
* **Environmental Consistency**: Flyway ensures identical database schema evolution across local development, CI pipelines, staging, and production environments.

## Consequences
* **Positive**: Complete schema versioning history, zero accidental DDL mutations, production-grade deployment safety.
* **Negative / Trade-offs**: Schema changes require writing explicit SQL migration scripts alongside entity modifications.
