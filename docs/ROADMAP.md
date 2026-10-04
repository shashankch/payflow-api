# Payflow API — Engineering Roadmap

A structured, phased evolution plan from baseline REST API to an enterprise-grade transactional ledger and payment backend.

!!! note "Status Legend"
    - :white_check_mark: **Complete** &nbsp;&nbsp;|&nbsp;&nbsp; :arrows_counterclockwise: **In Progress** &nbsp;&nbsp;|&nbsp;&nbsp; :white_large_square: **Planned**

---

## Phase 0 — Baseline Architecture ✅

Initial User and Transaction REST endpoints with in-memory storage and layered application architecture.

---

## Phase 1 — Project Hygiene & Engineering Tooling ✅

Foundation setup adhering to modern Java standards:

- **Build & Standards**: GraalVM / JDK 25 alignment, Maven build configuration, MIT licensing, and semantic changelog.
- **Code Quality & Static Analysis**: Automated formatting with Spotless (Eclipse formatter) and Checkstyle rules bound to Maven build phases.
- **CI/CD Pipeline**: GitHub Actions continuous integration workflow verifying formatting, linting, and multi-tier test suites.

---

## Phase 2 — Domain Modeling & API Hardening ✅

Enterprise domain modeling and API isolation:

- **Rich Domain Model**: Financial precision via `BigDecimal` denominated in Indian Rupees (INR, ₹), Jakarta validation constraints, JPA foreign key integrity, and domain state invariants.
- **DTO Isolation & Versioning**: URI-based `/api/v1` API versioning, input validation, capped pagination, and compile-time MapStruct mapping.
- **Error Handling Framework**: Centralized RFC 9457 (obsoleting RFC 7807) `ProblemDetail` error responses with correlation IDs (`X-Request-Id`).
- **Opaque Resource References**: Non-enumerable UUID reference IDs insulating internal database primary keys from public exposure.
- **API Documentation**: Automated OpenAPI 3.0 specification and interactive Swagger UI playground.

---

## Phase 3 — ACID Transfers, Concurrency & Double-Entry Ledger ✅

Financial transaction engine guarantees:

- **Atomic Money Transfers**: ACID transfer orchestration denominated in Indian Rupees (INR, ₹) and append-only refund semantics within transactional boundaries.
- **Pessimistic Concurrency Control**: Row-level database write locking (`SELECT ... FOR UPDATE`) with deterministic alphabetical lock acquisition to eliminate deadlocks and race conditions.
- **Immutable Balance Ledger**: Double-entry bookkeeping recording paired debit/credit entries, balance audits, and cryptographic auditability.

---

## Phase 4 — Database Migrations & Environment Profiles ✅

Cloud-native persistence and test parity:

- **Database Schema Management**: Versioned Flyway SQL migrations, performance indexing, and Hibernate validation.
- **Spring Profiles**: Environment-specific configurations for `local` (H2 zero-dependency dev), `test`, and `prod` (PostgreSQL).
- **Testcontainers Infrastructure**: Production-parity PostgreSQL containerized testing integrated into build verification.

---

## Phase 5 — Multi-Tier Testing Strategy ✅

Comprehensive automated quality verification:

- **Isolated Unit & Slice Testing**: Service logic unit testing with Mockito, Spring `@WebMvcTest` controller slices, and `@DataJpaTest` repository verification.
- **Concurrency & Integration Tests**: Multi-threaded race condition tests (10 concurrent threads), cross-transfer deadlock avoidance validation, and balance ledger reconciliation against containerized PostgreSQL.

---

## Phase 6 — Durable Idempotency & Transactional Outbox ✅

Fault-tolerant mutation guarantees and event persistence:

- **Durable Idempotency Engine**: Mandatory `Idempotency-Key` tracking with SHA-256 request payload hashing, cached response replay, in-flight conflict rejection, and background TTL cleanup.
- **Spring Modulith Transactional Outbox**: Event Publication Registry atomically saving domain events to the database outbox log within transfer transactions, paired with async in-process listeners and module boundary verification.

---

## Phase 7 — Security, Authorization & Outbound Integration ✅

Identity, access control, and resilient integrations:

- **Stateless Authentication**: Spring Security integration with HMAC-SHA256 JWT tokens, login endpoints, and secure filter chains.
- **Principal-Bound Authorization**: Strict sender verification, multi-party transaction visibility (sender/receiver only), and ledger privacy controls.
- **Declarative External Client**: Outbound HTTP Interface Client backed by `RestClient` for external UPI verification, featuring exponential backoff retry with jitter and non-blocking graceful fallback.

---

## Phase 8 — Observability, Resilience, Caching & Distributed Locking ✅

Enterprise reliability and distributed coordination:

- **Full-Stack Observability**: Native ECS JSON structured logging in production, MDC correlation tags, Prometheus metrics, HikariCP pool monitoring, and OpenTelemetry distributed tracing.
- **Resilience4j Fault Tolerance**: Dynamic per-user rate limiting (10 req/s with RFC 6585 headers), circuit breaking on external services, timeout policies, and automated memory eviction.
- **Distributed Caching**: Profile-conditional cache-aside pattern leveraging Redis with JSON serialization in production and local in-memory Caffeine fallback in non-production.
- **Distributed Locking**: Redisson distributed locking for multi-instance idempotency coordination with bounded acquisition timeouts, fail-safe lease durations, and no-op local fallback.

---

## Phase 9 — Event Streaming, Outbox Lifecycle & Production Hardening ✅

Decoupled event-driven architecture and production resilience:

- **9A — Kafka via Spring Modulith Event Externalization**: Bridge domain events from transactional outbox to partitioned Apache Kafka topics with at-least-once delivery guarantees.
- **9B — Production Hardening & Security Audit**: Fail-fast secret validation, BOLA protection, targeted cache invalidation, and automated outbox retention cleanup.

---

## Phase 10 — Financial Intelligence & Performance Optimization ✅

Intelligent automation and runtime scalability:

- **10A — Gen-AI Spend Categorization**: Spring AI spend classification and actionable budgeting advice with circuit-breaker-backed heuristic fallback.
- **10B — Virtual Threads & Resource Tuning**: High-concurrency Java 25 Virtual Threads, bounded HikariCP connection pools, and lightweight `prod-light` profile.

---

## Phase 11 — Containerization, Kubernetes & Production Infrastructure ✅

Cloud-native packaging, orchestration, and automated quality gates:

- **11A — Multi-Stage Containerization & Local Orchestration**: Minimal multi-stage Docker build (Java 25 JRE, non-root user, healthcheck) and full-stack Docker Compose (PostgreSQL, Redis, Kafka, Ollama, Prometheus, Grafana).
- **11B — Cloud-Native Kubernetes Deployment**: Declarative Kubernetes manifests (Deployments, Services, ConfigMaps, Secrets, HPA, PDB) with graceful shutdown coordination.
- **11C — CI/CD Pipeline Hardening & Quality Gates**: Automated static analysis (SpotBugs) and strict code coverage enforcement (JaCoCo) integrated into GitHub Actions.
- **11D — Documentation Modernization & Declarative D2 Diagrams**: Modernized public documentation portal with Material for MkDocs, declarative D2 architecture diagrams with zero drift enforcement, and bidirectional JaCoCo code coverage navigation.

---

## Phase 12 — Production Readiness & Release Finalization ⬜ (Next Up)

Final verification and public milestone release:

- **12A — End-to-End System Smoke Verification**: Clone-and-run verification, container smoke tests, and OpenAPI validation.
- **12B — Documentation Finalization & Open-Source Release**: Comprehensive documentation audit and semantic version release `v1.0.0`.
