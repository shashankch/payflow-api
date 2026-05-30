# Payflow API — Backend Service (v0)

Payflow is a growing transactional payments backend. This repository captures the current phase 0 implementation and a clear, phase-based path to production-grade maturity.

## Project snapshot

This project is designed to evolve from a bare-minimum backend into a production-ready service supporting:

- reliable, idempotent money transfers
- concurrency-safe balance updates
- audit-style transaction records
- strong persistence and schema management
- event-driven integration and async notifications
- caching, rate limiting, and observability
- Docker-based deployment and cloud readiness
- end-to-end testing and advanced extension points

## Current scope

Phase 0 is intentionally focused on the core business flow:

- Java + Spring Boot 4.x
- H2 in-memory database for local development
- Layered `Controller` → `Service` → `Repository` architecture
- Basic user and transaction APIs
- Single-transaction transfer flow
- Duplicate-safe transfer requests
- Local smoke tests and Maven build support

## Status

- Version: v0 (phase 0 baseline)
- Runtime target: Java 25 (recommended stable target: Java 21)
- Framework: Spring Boot 4.x
- Build: Maven 3.9.x
- Test command: `mvn -q clean test`

## Quick start

Build and run tests:

```bash
mvn clean install
mvn -q clean test
```

Run locally:

```bash
mvn spring-boot:run
# http://localhost:8080
```

H2 console (development only):

```text
http://localhost:8080/h2-console
JDBC URL: jdbc:h2:mem:payupidb
Username: user
Password: user
```

## Architecture overview

The current implementation is intentionally simple:

- Controllers handle HTTP routing and request validation.
- Services contain business logic and transaction boundaries.
- Repositories use Spring Data JPA for persistence.
- Transfers execute as a single atomic unit.
- H2 is used for local iteration; production persistence is planned later.

For more detail, see `docs/ARCHITECTURE.md`.

## Current limitations

This phase 0 baseline intentionally does not include:

- production-grade PostgreSQL deployment or DB migration tooling
- a full authentication and authorization system
- distributed observability and metrics pipelines
- containerized or cloud deployment artifacts
- advanced feature rollout and API versioning
- end-to-end testing around a full stack
- AI-powered extension features

These capabilities are intentionally deferred to later phases.

## Build and run

### Fast local development (with DevTools)

For rapid iteration, use the Spring Boot maven plugin directly:

```bash
mvn spring-boot:run
```

Spring Boot DevTools is included for automatic reload on classpath changes.

### Docker Compose (minimal setup)

Build a regular JVM image and run via Docker Compose:

```bash
mvn clean package -DskipTests
docker-compose up --build
```

The application will be available at http://localhost:8080.

### Cloud Native Buildpacks

Build an optimized OCI image for cloud deployment:

```bash
mvn spring-boot:build-image
docker run -p 8080:8080 payflow-api:latest
```

Buildpacks handle all optimization, layering, and cloud-native conventions automatically.

### GraalVM native image (optional)

For ultra-fast startup and minimal memory footprint:

```bash
mvn -Pnative native:compile
./target/payflow-api

# this will use Buildpacks with the native profile
mvn -Pnative spring-boot:build-image
```

Note: native-image builds are platform-specific and take longer to compile. Use buildpacks for most cloud deployments.

## API surface

The phase 0 API includes:

- `POST /users` — create a user
- `GET /users` — list users
- `GET /users/{id}` — get user by ID
- `GET /users/upi/{upiId}` — get user by UPI ID
- `GET /users/balance/{amount}` — find users with balance above a threshold
- `POST /transactions` — create a transfer between users

See `src/main/java/com/payflow/controller` for controllers, DTOs, and validation logic.

## Roadmap overview

This repo is a phase-based project with a defined upgrade path from bare-minimum baseline to production-ready service.

The next phases include:

- **Phase 1 — Foundation**: ACID transfer logic, PostgreSQL, Flyway, locking/concurrency, and database-level stability.
- **Phase 2 — Platform maturity**: JWT auth, Redis caching, rate limiting, observability, actuator, and API documentation/versioning.
- **Phase 3 — Deployment readiness**: Kafka event-driven architecture, Docker, cloud deployment guidance, end-to-end tests, and release staging.
- **Phase 4 — Advanced extension**: AI-assisted features, advanced monitoring, deployment/versioning workflows, and production polish.

For the detailed phase plan, see `docs/ROADMAP.md`.
