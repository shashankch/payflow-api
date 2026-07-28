# Payflow API — Backend Service

![Build](https://github.com/shashankch/payflow-api/actions/workflows/ci.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-6DB33F?logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-4169E1?logo=postgresql&logoColor=white)
![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)
![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)

Payflow is a highly resilient, transactional payments backend built using **Java 25** and **Spring Boot 4.x**. The project is designed with a pluggable, cloud-flexible architecture optimized for local developer iteration, Kubernetes deployment, and resource-constrained environments (like AWS Free Tier and Oracle Cloud Always Free).

---

## Current Status & Evolution Roadmap

The project is currently evolving through a phased implementation roadmap from a Phase 0 baseline into an enterprise-grade payment system.

- **Phase 0 (Completed)**: Baseline REST API for User & Transaction CRUD with H2 in-memory storage.
- **Phase 1 (In Progress)**: Project hygiene, static analysis (Spotless, Checkstyle), and GitHub Actions CI.
- **Target Phases**: PostgreSQL & Flyway migrations, pessimistic row locking with deadlock avoidance, durable idempotency engine, transactional outbox pattern, JWT security, Resilience4j fault tolerance, Redis caching & Redisson distributed locking, Kafka event streaming, and Gen-AI transaction insights.

See the complete step-by-step evolution in **[Phased Roadmap](docs/ROADMAP.md)**.

---

## Technical Architecture Highlights

- **ACID Transaction Hardening**: Enforces sequential consistency under race conditions using database-level pessimistic locking (`SELECT FOR UPDATE`).
- **Distributed Locking Coordination**: Integrates **Redis Redlock** (via Redisson) to synchronize distributed triggers across multi-instance deployments.
- **JPA N+1 Resolution**: Avoids Hibernate lazy loading bottlenecks through explicit **Fetch Joins** and `@EntityGraph` definitions.
- **REST API Versioning**: Implements strict URI version control (`/api/v1`) protecting endpoints from breaking changes.
- **Gen-AI Spending Assistant**: Integrates **Spring AI** (Ollama / Groq / Gemini) to automatically categorize transactions and generate spend analysis.
- **Durable Idempotency Engine**: Enforces exactly-once execution on mutation APIs via SHA-256 request payload hashing and persistent state checking.
- **Transactional Outbox Pattern**: Guarantees atomic consistency between state transitions and event streaming without dual-write vulnerabilities.
- **Pluggable Resilience Policies**: Configured via Resilience4j for client-side rate-limiting, exponential backoff retries with jitter, and call timeouts.
- **Telemetry & Observability**: Integrated with Spring Boot Actuator, Prometheus metrics, Logback MDC-based distributed trace correlation, and Grafana dashboards.

---

## Project Documentation Directory

1. 📘 **[System Architecture Guide](docs/ARCHITECTURE.md)**: Deep technical details on topology, locking, idempotency, Spring profiles, and observability.
2. 🗓️ **[Phased Implementation Roadmap](docs/ROADMAP.md)**: Subdivided step-by-step phases (A and B) spanning database migrations, security, event streaming, and deployment.
3. 🌐 **[REST API Specification](docs/API_SPECIFICATION.md)**: Complete request and response payload schemas, versioned endpoints, validations, status codes, and RFC 7807 error formats.
4. 📋 **[Engineering Conventions](docs/CONVENTIONS.md)**: Coding standards, Git commit conventions, PR guidelines, and test naming rules.
5. 📜 **[Architecture Decision Records](docs/ADR.md)**: Log of architectural decisions, context, trade-offs, and consequences.

---

## Spring Profiles Strategy

The application leverages profile isolation to adapt resources to the target environment dynamically:

- **`local` (Default)**: In-memory H2 database, automated hibernate schema updates, and zero-dependency local startup.
- **`test`**: Active during integration tests, utilizing **Testcontainers** to dynamically boot local Postgres/Kafka container instances for validation.
- **`prod`**: Production-grade settings. Database credentials and broker addresses are read from system environment variables. Schema evolution is strictly managed by **Flyway migrations**.
- **`prod-light` (Resource Constrained / Free Tier)**: Memory-optimized profile for single constrained virtual servers (1 GiB RAM). Uses lightweight in-memory fallbacks for Redis and Kafka to prevent OOM failures.

---

## Quick Start

### 1. Build and Run Local Tests
Requires JDK 25 and Maven:
```bash
mvn clean install
mvn test
```

### 2. Local Standalone Startup (Zero Dependencies)
Boots the application on port `8080` using H2 in-memory storage:
```bash
mvn spring-boot:run
```
- **H2 Console**: Available at `http://localhost:8080/h2-console`
  - *JDBC URL*: `jdbc:h2:mem:payupidb`
  - *Credentials*: `user` / `user`

### 3. Local Full-Stack Ecosystem Startup (Docker Compose)
To run a local staging environment containing Postgres, Redis, Kafka KRaft, Prometheus, and Grafana:
```bash
docker-compose up --build
```

---

## Production Deployment Readiness

### Kubernetes Deployment
Static Kubernetes manifests are defined under the `k8s/` directory. Deploy using:
```bash
kubectl apply -f k8s/
```
- **Liveness Probe**: Routed to `/actuator/health/liveness`
- **Readiness Probe**: Routed to `/actuator/health/readiness`
- **Graceful Shutdown**: Handled via `server.shutdown=graceful` coordinating with container orchestration to process in-flight transactions before pod termination.

### GraalVM Native Compilation
To compile the application to a lightweight, platform-native binary that starts in milliseconds and consumes ~30-50MB of RAM:
```bash
# Compile native binary locally
mvn -Pnative native:compile

# Or build an optimized native OCI image via Buildpacks
mvn -Pnative spring-boot:build-image
```

---

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
