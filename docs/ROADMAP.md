# Payflow API — Roadmap

This document lays out a practical phase-wise plan for moving Payflow from a phase 0 baseline toward a production-grade transactional backend.

## Phase 0 — Baseline implementation (current)

### Goals

- Establish a working transactional payments backend.
- Keep the architecture small and maintainable.
- Provide a stable local developer experience.

### Current deliverables

- Basic user and transaction APIs.
- In-memory H2 persistence for local execution.
- Single-transaction transfer flow.
- Layered service/repository architecture.
- Local smoke tests and Maven build support.

### Acceptance criteria

- The app starts cleanly with `mvn spring-boot:run` or `docker-compose up`.
- Transfers update both accounts and record the transaction entry.
- Duplicate transfer requests do not create duplicate movements.
- The current endpoints work as documented.
- Health check endpoint is available at `/actuator/health` (actuator included).

## Phase 1 — Core production foundation

### Goals

- Introduce production-grade persistence and database stability.
- Harden transfer correctness and concurrency behavior.
- Add schema management and repeatable migrations.

### Work items

- Add PostgreSQL support and a production profile.
- Introduce Flyway-managed database migrations.
- Enhance transfer logic for ACID-safe balance updates.
- Add explicit locking or concurrency controls for user balances.
- Implement a persistent idempotency key store (DB or Redis) and request deduplication to support exactly-once semantics on retries.
- Add integration tests using Testcontainers for Postgres.
- Validate schema consistency and data persistence across restarts.

### Acceptance criteria

- The app can run against a Postgres database.
- Migrations apply cleanly on a fresh database.
- Transfer invariants hold under integration tests.
- The app persists user and transaction data correctly.

## Phase 2 — Security and platform maturity

### Goals

- Add authentication and authorization.
- Improve runtime visibility and operational confidence.
- Add caching and request protection.

### Work items

- Implement JWT-based auth for protected endpoints.
- Add Redis caching for read-heavy queries.
- Introduce rate limiting for transfer requests.
- Expand actuator endpoints for detailed metrics and readiness probes.
- Add Swagger/OpenAPI documentation and API versioning.
- Add structured logging with correlation IDs and runtime configuration profiles.

### Acceptance criteria

- Protected endpoints require valid authentication.
- Caching improves read performance while preserving freshness.
- Rate limiting protects key APIs from repeated abuse.
- The app exposes health, metrics, and API documentation.

## Phase 3 — Event-driven deployment readiness

### Goals

- Add async integration and cloud deployment support.
- Make the service deployable in modern container/cloud environments.
- Expand testing to full-stack end-to-end scenarios.

### Work items

- Add Kafka-driven events for transaction and user state changes.
- Finalize Cloud Native Buildpack configuration for optimized OCI images.
- Add cloud deployment notes for managed Kubernetes or container hosts.
- Add end-to-end test coverage that exercises the full stack.
- Add deployment/versioning practices for APIs and database evolution.
- Validate GraalVM native image builds for fast startup scenarios (optional).

### Acceptance criteria

- Transaction events are published and can be consumed asynchronously.
- The app has a working Docker deployment path.
- Cloud deployment guidance is documented.
- End-to-end tests validate key user flows.
- Release and versioning practices are defined.

## Phase 4 — Advanced extension and AI readiness

### Goals

- Prepare Payflow for advanced features and next-level production polish.
- Add AI-ready extension points and advanced operational workflows.

### Work items

- Define AI-assisted features or analytics as extension points.
- Add observability for advanced metrics and feature rollout.
- Add packaging support for multi-stage deployments and versioned releases.
- Improve the domain model and deployment architecture for long-term evolution.

### Acceptance criteria

- The system architecture supports AI/analytics extensions.
- Monitoring and feature rollout paths are documented.
- The codebase supports staged deployment and release versioning.
- The project is positioned for production-grade maturity.

## How to use this roadmap

This roadmap is intended to keep the project focused and aligned with product maturity.

1. Deliver a reliable baseline.
2. Harden the persistence and transaction model.
3. Add security, caching, and operational maturity.
4. Enable event-driven deployment and full-stack testing.
5. Prepare for advanced, AI-enabled extensions.

Each phase is a deliberate step toward a production-ready backend.
