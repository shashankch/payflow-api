# Payflow API — Engineering Roadmap

A structured, phased evolution plan from baseline REST API to an enterprise-grade transactional ledger and payment backend.

### Status Legend
- ✅ Complete &nbsp; 🔄 In Progress &nbsp; ⬜ Planned

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
- **Error Handling Framework**: Centralized RFC 7807 `ProblemDetail` error responses with correlation IDs (`X-Request-Id`).
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

## Phase 9 — Event Streaming & Messaging ✅
Decoupled event-driven architecture:
- **9A — Kafka via Spring Modulith Event Externalization**: Transparently bridge domain events from the transactional outbox registry to partitioned Apache Kafka topics with exactly-once producer semantics. Non-production environments continue seamless in-process event dispatching.

---

## Phase 10 — Financial Intelligence & Performance Optimization ⬜ (Next Up)
Intelligent automation and runtime scalability:
- **10A — Gen-AI Spend Categorization & Financial Insights**: Spring AI integration providing automated expenditure classification and contextual budgeting tips with structured JSON output, guarded by circuit breakers and heuristic fallback.
- **10B — Virtual Threads & Resource Tuning**: Enable Java 25 Virtual Threads for high-concurrency throughput, introduce a lightweight single-instance production profile (`prod-light` for 1 GiB RAM), and optimize HikariCP connection pooling and JVM memory bounds.

---

## Phase 11 — Containerization, Kubernetes & Production Infrastructure ⬜
Cloud-native packaging and deployment:
- **11A — Multi-Stage Containerization & Local Orchestration**: Secure multi-stage Docker build with non-root runtime, paired with full-stack Docker Compose orchestrating PostgreSQL, Redis, Kafka, Ollama (offline local AI), Prometheus, and Grafana.
- **11B — Cloud-Native Kubernetes Deployment**: Production Kubernetes manifests (Deployments, Services, ConfigMaps, Secrets, HPA) configured with HTTP health probes, graceful shutdown, and container resource limits.
- **11C — CI/CD Pipeline Hardening & Quality Gates**: Automated static bytecode analysis (SpotBugs) and strict code coverage thresholds (JaCoCo) integrated into the continuous integration pipeline.

---

## Phase 12 — Production Readiness & Release Finalization ⬜
Final verification and public milestone release:
- **12A — End-to-End System Smoke Verification**: Comprehensive verification of clean clone-and-run workflows, multi-container smoke tests, and OpenAPI endpoint validation.
- **12B — Documentation Finalization & Open-Source Release**: Comprehensive documentation audit across all architecture diagrams and decision logs, accompanied by semantic version bump to `v1.0.0`.
