# Payflow API — Backend Service

<p align="left">
  <a href="https://github.com/shashankch/payflow-api/actions/workflows/ci.yml"><img src="https://github.com/shashankch/payflow-api/actions/workflows/ci.yml/badge.svg" alt="Build"></a>
  <a href="https://dev.java/"><img src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white" alt="Java 25"></a>
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring%20Boot-4.1.0-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License: MIT"></a>
  <a href="CONTRIBUTING.md"><img src="https://img.shields.io/badge/PRs-welcome-brightgreen.svg" alt="PRs Welcome"></a>
</p>

Payflow is a transactional payments backend built using **Java 25** and **Spring Boot 4.x**, designed to evolve from a baseline REST API into an enterprise-grade payment system with ACID guarantees, event-driven architecture, and observability.

---

## Current Status

The project is evolving through a phased implementation roadmap. See the full plan in **[Phased Roadmap](docs/ROADMAP.md)**.

| Phase | Description | Status |
| :--- | :--- | :--- |
| **Phase 1: Foundation & Project Hygiene** | Spring Boot 4.1, JDK 25, H2, DevTools, Checkstyle, Spotless, CI Pipeline | ✅ Complete |
| **Phase 2: Domain Modeling & API Hardening** | Entities, Repositories, DTOs, Mappers, OpenAPI Docs, RFC 7807 Error Handling, UUIDs | ✅ Complete |
| **Phase 3: ACID Transactions & Concurrency** | Money Transfer Orchestration (`@Transactional`), Pessimistic Locking (`FOR UPDATE`), Deadlock Avoidance, Double-Entry Balance Ledger | ✅ Complete |
| **Phase 4: Database & Profiles** | Flyway Migrations, PostgreSQL Integration, Profile Configs (`local`/`test`/`prod`), Testcontainers | 🔄 In Progress (4A Done) |

---

## Implemented Features

- **Flyway Schema Migrations & PostgreSQL Support**: Version-controlled DDL migrations (`V1` through `V4`) managing `users`, `transactions`, and `balance_ledger` schemas with performance indexes, structured YAML configuration (`application.yml`), and Hibernate `ddl-auto=validate` enforcement.
- **Double-Entry Balance Ledger**: Immutable audit trail (`balance_ledger` table) recording `DEBIT` and `CREDIT` entries with `balanceBefore` and `balanceAfter` tracking per transaction for financial auditability and balance reconciliation.
- **Pessimistic Locking & Deadlock Avoidance**: Database-level write locking (`SELECT ... FOR UPDATE`) via `UserRepository.findByUpiIdWithLock()` with deterministic alphabetical lock ordering by UPI ID to prevent race conditions and cross-transfer deadlocks.
- **JPA N+1 Resolution**: `@EntityGraph(attributePaths = {"sender", "receiver"})` on transaction repository queries ensuring single-query JOIN fetches.
- **Money Transfer Orchestration**: `TransactionService.sendMoney()` executing under `@Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class, timeout = 5)` boundaries with balance debit/credit invariance.
- **RFC 7807 Exception Framework**: Centralized `@RestControllerAdvice` handling domain exceptions (`UserNotFoundException`, `InsufficientBalanceException`, `DuplicateUpiIdException`, `SelfTransferException`) and field-level validation errors.
- **Request Correlation Tracking**: `RequestIdFilter` (`OncePerRequestFilter`) injecting `X-Request-Id` UUID into MDC context and HTTP response headers.
- **Interactive API Docs & Swagger UI**: Auto-generated live OpenAPI 3.0 specs via Springdoc at `http://localhost:8080/swagger-ui.html` and `/v3/api-docs`.
- **MapStruct Compile-Time Mapping**: Zero-reflection type-safe DTO <-> Entity mappings generated during Maven build.
- **DTO Isolation & API Versioning**: `/api/v1/` URI paths using Java records (`UserResponse`, `TransactionResponse`, `PagedResponse`) and validated request DTOs (`CreateUserRequest`, `TransferMoneyRequest`).
- **Input Validation**: Enforced via Jakarta Validation (`@Valid`, `@NotBlank`, `@Pattern`, `@DecimalMin`, `@Size`, `@Min`, `@Max`).
- **Rich Domain Model**: Entities encapsulate domain invariants (`User.debit()`, `User.credit()`) and balance validation.
- **Financial Precision**: All monetary values mapped using `BigDecimal` (`precision = 19, scale = 4`) to prevent floating-point rounding errors.
- **Entity Hardening**: Audit timestamps (`createdAt`, `updatedAt`), optimistic locking (`@Version`), JPA `@ManyToOne` foreign key constraints, UUID reference IDs, and transaction status/type enums.
- **Constructor Injection**: Enforced across all service and controller components for immutability and testability.
- **REST API**: CRUD endpoints under `/api/v1/users` and `/api/v1/transactions`.
- **Layered Architecture**: Controller → Service → Repository pattern with Spring Data JPA.
- **In-Memory Database**: H2 with auto-generated schema for zero-dependency local development.
- **Code Quality**: Spotless (Eclipse formatter) + Checkstyle enforced at Maven `validate` phase.
- **CI Pipeline**: GitHub Actions workflow running `mvn clean verify` on push/PR to `main`.
- **Actuator**: Health, info, and metrics endpoints exposed.

---

## Target Architecture (Roadmap)

The following features are planned and will be implemented across future phases:

- ACID transaction hardening with pessimistic locking and deadlock avoidance
- Balance ledger with double-entry bookkeeping for auditability
- PostgreSQL with Flyway-managed schema migrations
- Durable idempotency engine with SHA-256 payload hashing
- Spring Modulith modular monolith with event publication registry (transactional outbox)
- JWT authentication and authorization
- RestClient + HTTP Interface Client (`@GetExchange`/`@PostExchange`) for outbound service calls
- Framework 7 native `@Retryable` with exponential backoff + jitter for external service resilience
- Resilience4j fault tolerance (per-user rate limiting, circuit breaker)
- Redis caching and distributed locking
- Apache Kafka event streaming via Spring Modulith event externalization
- Structured logging with MDC trace correlation and Prometheus metrics
- OpenTelemetry distributed tracing via Micrometer bridge (W3C `traceparent`)
- Multi-stage Docker build with full-stack Docker Compose (PostgreSQL, Redis, Kafka, Prometheus, Grafana)
- Kubernetes manifests with health probes and graceful shutdown
- Gen-AI spend insights with Spring AI

See **[System Architecture Guide](docs/ARCHITECTURE.md)** for detailed design documentation.

---

## Project Documentation

| Doc | Description |
|-----|-------------|
| 📘 [System Architecture](docs/ARCHITECTURE.md) | Design patterns, concurrency control, observability |
| 🗓️ [Phased Roadmap](docs/ROADMAP.md) | Step-by-step evolution plan |
| 🌐 [API Specification](docs/API_SPECIFICATION.md) | Endpoints, payloads, validation rules, error formats |
| 📋 [Conventions](docs/CONVENTIONS.md) | Coding standards, Git workflow, testing rules |
| 📜 [Architecture Decisions](docs/ADR.md) | Decision records with context and trade-offs |

---

## Quick Start

### Prerequisites
- JDK 25 (Temurin recommended)
- Maven 3.9+

### Build & Run
```bash
# Build and run tests
mvn clean verify

# Start locally (H2 in-memory, port 8080)
mvn spring-boot:run
```

- **Swagger UI (Interactive API Docs)**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI 3.0 JSON Spec**: `http://localhost:8080/v3/api-docs`
- **H2 Console**: `http://localhost:8080/h2-console`
  - JDBC URL: `jdbc:h2:mem:payupidb`
  - Credentials: `user` / `user`

### Docker
```bash
docker-compose up --build
```

---

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
