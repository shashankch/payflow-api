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
|-------|-------------|--------|
| **Phase 0** | Baseline REST API — User & Transaction CRUD with H2 in-memory storage | ✅ Complete |
| **Phase 1** | Project hygiene — Spotless, Checkstyle, GitHub Actions CI | ✅ Complete |
| **Phase 2A** | Domain model hardening — `BigDecimal` financials, rich domain methods, audit timestamps | ✅ Complete |
| **Phase 2B** | DTO layer, Jakarta validation, URI versioning (`/api/v1/`), response records | ✅ Complete |
| **Phase 2C** | MapStruct compile-time DTO mappers, interactive OpenAPI/Swagger UI (`/swagger-ui.html`) | ✅ Complete |
| **Phase 2D+** | Custom exception handling, RFC 7807 problem details, optimistic locking, security, observability | 🔄 In Progress |

---

## Implemented Features

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
- DTO layer with input validation and RFC 7807 error responses
- PostgreSQL with Flyway-managed schema migrations
- Durable idempotency engine with SHA-256 payload hashing
- Transactional outbox pattern for reliable event streaming
- JWT authentication and authorization
- Resilience4j fault tolerance (rate limiting, retry, circuit breaker)
- Redis caching and distributed locking
- Kafka event streaming
- Structured logging with MDC trace correlation and Prometheus metrics
- Multi-stage Docker build with full-stack Docker Compose
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
