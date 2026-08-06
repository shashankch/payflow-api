# Payflow API — Roadmap

A phased evolution plan from baseline REST API to enterprise-grade transactional backend. Each sub-phase maps to a single pull request.

### Status Legend
- ✅ Complete &nbsp; 🔄 In Progress &nbsp; ⬜ Not Started

---

## Phase 0 — Baseline ✅

Basic User and Transaction REST endpoints with H2 in-memory storage and layered architecture.

---

## Phase 1 — Project Hygiene & Dev Tooling ✅

### 1A — Project Scaffolding ✅
MIT license, changelog, `.editorconfig`, ADR log, engineering conventions, GitHub badges.

### 1B — Code Formatting & Static Analysis ✅
Spotless (Eclipse formatter) and Checkstyle bound to Maven `validate` phase.

### 1C — CI Pipeline ✅
GitHub Actions workflow with JDK 25, dependency caching, `mvn clean verify`.

### 1D — Fixes ✅
Dockerfile JDK alignment, GraalVM profile fix, README cleanup.

---

## Phase 2 — Domain Model & API Hardening 🔄

### 2A — Entity Model & Rich Domain ✅
`BigDecimal` financials, `@Column` constraints, JPA FK relationships, audit timestamps, status/type enums, rich domain methods (`debit`/`credit`), constructor injection.

### 2B — DTO Layer & Validation ✅
Request/response DTOs, Jakarta validation, `/api/v1/` versioning, pagination with max limits.

### 2C — Mappers & API Docs ✅
MapStruct compile-time mappers, OpenAPI/Swagger UI via `springdoc-openapi`.

### 2D — Error Handling ✅
Custom exception hierarchy, RFC 7807 `ProblemDetail` responses, `X-Request-Id` correlation.

### 2E — Model Refinements & Service Hardening ✅
UUID reference IDs for users (replacing auto-increment exposure), `@Transactional(readOnly)` on reads, domain exception consolidation, `@DecimalMax` transfer cap, MDC logging pattern.

---

## Phase 3 — ACID Transactions & Concurrency 🔄

### 3A — Transfer & Refund Logic ✅
End-to-end transfer orchestration within `@Transactional` boundaries, append-only refund semantics.

### 3B — Pessimistic Locking ⬜
`SELECT ... FOR UPDATE` with deterministic lock ordering, `@EntityGraph` for N+1 resolution.

### 3C — Balance Ledger ⬜
Double-entry bookkeeping (DEBIT/CREDIT entries), `balanceBefore`/`balanceAfter` audit trail.

---

## Phase 4 — Database & Profiles ⬜

### 4A — Flyway & PostgreSQL ⬜
Versioned SQL migrations, PostgreSQL driver, YAML configuration, performance indexes.

### 4B — Spring Profiles & Testcontainers ⬜
Profile-specific configs (`local`/`test`/`prod`), Testcontainers PostgreSQL for integration tests.

---

## Phase 5 — Testing ⬜

### 5A — Unit Tests ⬜
Mockito service tests, `@WebMvcTest` controller tests, `@DataJpaTest` repository tests.

### 5B — Integration & Concurrency Tests ⬜
Full lifecycle tests on Testcontainers PG, 10-thread race condition tests, balance reconciliation assertions.

---

## Phase 6 — Idempotency & Outbox ⬜

### 6A — Idempotency Engine ⬜
`Idempotency-Key` header filter, SHA-256 payload hashing, cached response replay, TTL cleanup.

### 6B — Transactional Outbox ⬜
Outbox table with `SKIP LOCKED` polling, pluggable event publisher interface, in-memory default.

---

## Phase 7 — Security ⬜

### 7A — JWT Authentication ⬜
Spring Security, stateless JWT, auth/login endpoint, CORS configuration.

### 7B — Authorization ⬜
Sender verification, transaction/ledger history access control.

---

## Phase 8 — Observability, Resilience & Caching ⬜

### 8A — Logging & Metrics ⬜
JSON-structured logging, MDC trace correlation, Prometheus metrics, HikariCP monitoring.

### 8B — Resilience4j ⬜
Per-user rate limiting, retry with exponential backoff, timeout policies.

### 8C — Caching (Redis) ⬜
Cache-aside pattern, `@CacheEvict` on writes, Caffeine fallback for local/test.

### 8D — Distributed Locking ⬜
Redisson Redlock for multi-instance idempotency coordination, NoOp fallback.

---

## Phase 9 — Event Streaming ⬜

### 9A — Kafka Integration ⬜
Kafka event publisher (`acks=all`, idempotent producer), Testcontainers Kafka tests.

---

## Phase 10 — Infrastructure & Deployment ⬜

### 10A — Docker ⬜
Multi-stage Dockerfile, full-stack Docker Compose (PG, Redis, Kafka, Prometheus, Grafana).

### 10B — Kubernetes ⬜
Deployment, Service, ConfigMap, Secret, HPA manifests with health probes.

### 10C — CI/CD Hardening ⬜
SpotBugs, JaCoCo coverage thresholds (80% line, 70% branch), artifact archiving.

---

## Phase 11 — Gen-AI Insights ⬜

### 11A — Spend Categorization ⬜
Spring AI with provider-agnostic config (Gemini/Groq/Ollama), circuit breaker fallback, feature flag.

---

## Phase 12 — Production Hardening ⬜

### 12A — Performance Tuning ⬜
`prod-light` profile (1 GiB RAM), virtual threads, HikariCP tuning, connection leak detection.

### 12B — Documentation Finalization ⬜
Final review of all docs, diagrams, and README. Clone-and-run validation.
