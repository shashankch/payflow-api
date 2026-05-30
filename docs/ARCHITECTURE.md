# Payflow API — Architecture

This document captures the current runtime design for Payflow and the architecture direction for the next production-grade phases.

## Purpose

Payflow is a transactional payments backend built with a deliberate phase-based progression from an initial baseline to a production-ready architecture.

The current architecture is intentionally lean, with future phases introducing stronger persistence, security, caching, event-driven integration, observability, and deployment readiness.

## Project vision

The long-term project goal is to deliver a production-ready payments backend that includes:

- strong domain handling and ACID-safe transfer logic
- PostgreSQL persistence with schema migrations
- concurrency and locking controls for balance updates
- JWT-based authentication and authorization
- Redis caching and rate limiting
- Kafka-driven event notifications and decoupled workflows
- Docker and cloud deployment support
- observability, API documentation, and versioning
- system-level end-to-end tests and advanced extension points

## Domain model

- `User`: represents an account with a stable identifier, UPI address, and balance.
- `Transaction`: records a transfer between two users, including amount, source, destination, and timestamps.

This domain model is intentionally compact to support correct transfer semantics while staying easy to extend.

## Layered design

The application follows a standard backend layering:

- `Controller` layer handles HTTP requests and input validation.
- `Service` layer contains business logic and transaction boundaries.
- `Repository` layer delegates persistence to Spring Data JPA.

This separation keeps controllers focused on HTTP, services focused on business rules, and persistence isolated behind repositories.

## Current baseline implementation

### Persistence

- H2 is used for local development and smoke tests.
- Spring Data JPA manages entity repositories for `User` and `Transaction`.
- The current profile is a simple in-memory database to support quick iteration.

### Transaction handling

- Transfer operations execute inside a single database transaction.
- Sender and receiver balance updates are performed together with the transaction record insert.
- This leaves the current flow atomic at the service layer.

### Idempotency

- The transfer endpoint is designed to support duplicate-safe requests.
- The current implementation avoids repeated transfer creation on retries.

Note: phase 0 does not include a persistent idempotency key store. Durable idempotency (storing idempotency keys in a database or external cache to survive restarts and coordinate across instances) is planned for Phase 1 alongside Postgres/Flyway and ACID transaction hardening.

### API coverage

The current API surface includes:

- `POST /users` — create a new user
- `GET /users` — list all users
- `GET /users/{id}` — retrieve a user by ID
- `GET /users/upi/{upiId}` — retrieve a user by UPI
- `GET /users/balance/{amount}` — find users with balance above a threshold
- `POST /transactions` — create a transfer between users

## Validation and correctness

- Input validation runs at the controller boundary.
- Business validation runs in the service layer.
- Current rules include positive amounts and valid user references.

## Testing strategy

- The project includes unit and integration smoke tests.
- Tests validate the core transfer flow and repository behavior.
- Run the test suite with:

```bash
mvn -q clean test
```

## Build and deployment practices (2026 standards)

### Development builds

Local development uses Spring Boot DevTools for fast iteration with automatic reload:

```bash
mvn spring-boot:run
```

### Container images

Two modern container image building approaches are supported:

- **Cloud Native Buildpacks** (recommended): Use `mvn spring-boot:build-image` to create optimized, layered OCI images following cloud-native conventions. Buildpacks handle JDK selection, dependency caching, and security patching automatically.
- **Traditional Dockerfile**: A simple Dockerfile is provided for custom image builds.

### GraalVM native images (optional)

GraalVM native-image builds are supported via the `native` Maven profile for ultra-fast startup and minimal memory footprint. Native builds are platform-specific and take longer to compile; buildpacks are generally preferred for cloud deployments.

## Future architecture direction

Planned architectural enhancements include:

- PostgreSQL persistence with Flyway-managed schema migration.
- Explicit locking and concurrency control for balance updates.
- JWT authentication and authorization for protected endpoints.
- Redis caching and rate limiting for performance and reliability.
- Kafka event-driven notifications for transaction events and decoupled consumers.
- Spring Boot Actuator metrics and health endpoints for operational visibility.
- Swagger/OpenAPI documentation and API versioning for client evolution.
- End-to-end testing with Testcontainers and full-stack validation.
- Advanced extension points such as AI-assisted analytics or feature recommendations.

## What this baseline is not

This phase 0 implementation is intentionally not yet a full production system. It does not yet include:

- managed production database deployments
- a complete security/auth stack
- distributed tracing, metrics, or alerting pipelines
- event streaming or async workflow orchestration
- Docker or cloud-native deployment manifests
- advanced end-to-end or AI-enhanced features

Those capabilities are planned for later phases.
