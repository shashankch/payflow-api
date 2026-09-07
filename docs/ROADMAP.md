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

## Phase 2 — Domain Model & API Hardening ✅

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

## Phase 3 — ACID Transactions & Concurrency ✅

### 3A — Transfer & Refund Logic ✅
End-to-end transfer orchestration within `@Transactional` boundaries, append-only refund semantics.

### 3B — Pessimistic Locking ✅
`SELECT ... FOR UPDATE` with deterministic lock ordering, `@EntityGraph` for N+1 resolution.

### 3C — Balance Ledger ✅
Double-entry bookkeeping (DEBIT/CREDIT entries), `balanceBefore`/`balanceAfter` audit trail, balance reconciliation query.

---

## Phase 4 — Database & Profiles ✅

### 4A — Flyway & PostgreSQL ✅
Versioned SQL migrations (`V1`..`V4`), PostgreSQL driver, structured YAML configuration (`application.yml`), performance indexes, Hibernate `ddl-auto=validate`.

### 4B — Spring Profiles & Testcontainers ✅
Profile-specific YAML configurations (`local`/`test`/`prod`), `AbstractIntegrationTest` base class with Testcontainers PostgreSQL (`postgres:16-alpine`), `@DynamicPropertySource`, and graceful fallback execution.

---

## Phase 5 — Testing ✅

### 5A — Unit & Slice Tests ✅
Mockito service unit tests (`UserServiceTest`, `TransactionServiceTest`), `@WebMvcTest` controller slice tests (`UserControllerTest`, `TransactionControllerTest` verifying RFC 7807 problem details and `GET /api/v1/users/{id}/ledger` pagination), `@DataJpaTest` repository slice tests (`UserRepository` and `BalanceLedgerRepository` JPQL reconciliation SUM query), MapStruct mapper tests.

### 5B — Integration & Concurrency Tests ✅
Full end-to-end money transfer lifecycle integration tests on Testcontainers PostgreSQL (`TransferLifecycleIT`), 10-thread synchronized race condition tests (`ConcurrentTransferIT` with CountDownLatch), deadlock avoidance cross-transfer tests (`MutualTransferDeadlockIT`), and double-entry balance ledger reconciliation assertions (`calculateReconciledBalanceByUserId()`).

---

## Phase 6 — Idempotency & Outbox ✅

### 6A — Idempotency Engine ✅
`Idempotency-Key` header filter with servlet request wrapping, deterministic SHA-256 payload hashing, cached HTTP 201 response replay, in-flight 409 conflict detection, key reuse rejection, Flyway migration (`V5__create_idempotency_registry.sql`), and background scheduled TTL cleanup with `idx_idemp_created` performance index.

### 6B — Spring Modulith Events & Transactional Outbox ✅
Spring Modulith event publication registry (`spring-modulith-starter-jpa`), domain event `TransferCompletedEvent` published via `ApplicationEventPublisher` inside `@Transactional` boundary, atomic persistence to `event_publication` table (`V6__create_event_publication_registry.sql`), async `@ApplicationModuleListener` handler (`TransferEventListener`), module boundary architectural verification (`ModulithStructureTest`), and restart replay configuration (`spring.modulith.events.republish-outstanding-events-on-restart`).

---

## Phase 7 — Security & External Integration ✅

### 7A — JWT Authentication ✅
Spring Security 6/7 integration, stateless HMAC-SHA256 JWT tokens via JJWT 0.13.0, `POST /api/v1/auth/login` endpoint issuing tokens with 1-hour TTL, `JwtAuthenticationFilter` Bearer token parsing and SecurityContext setup, `JwtAuthenticationEntryPoint` returning RFC 7807 401 Unauthorized problem details, CORS configuration, and public/protected security filter chain rule enforcement.

### 7B — Authorization ✅
Principal-bound resource authorization, sender verification on transfers, multi-party transaction visibility (sender/receiver only), double-entry balance ledger privacy, profile access control, and RFC 7807 `403 Forbidden` (`ForbiddenOperationException`, `JwtAccessDeniedHandler`).

### 7C — RestClient, HTTP Interface Client & External Service Integration ✅
Declarative HTTP Interface Client (`@HttpExchange`/`@GetExchange`) backed by `RestClient` for external UPI verification, Spring Retry `@Retryable` with exponential backoff and randomized jitter, graceful degradation fallback on service unavailability, and 422 Invalid UPI ID problem detail mapping.

---

## Phase 8 — Observability, Resilience, Caching & Distributed Locking ✅

### 8A — Logging, Metrics & Tracing ✅
JSON-structured logging (ECS in prod), MDC correlation (`requestId`, `traceId`, `spanId`, `http.status`, `http.latency_ms`), Prometheus metrics (`micrometer-registry-prometheus`, `/actuator/prometheus`), OpenTelemetry distributed tracing (`micrometer-tracing-bridge-otel`, `opentelemetry-exporter-otlp`), custom business metrics (`payflow.transfers.total`, `payflow.transfers.amount`, `payflow.transfers.duration` with p50/p95/p99 percentiles), HikariCP pool monitoring.

### 8B — Resilience4j ✅
Dynamic per-user rate limiting (`@PerUserRateLimiter`, 10 req/s, RFC 6585 `Retry-After: 1`, RFC 7807 429 ProblemDetail), circuit breaker on external UPI validation (`@CircuitBreaker(name = "upiValidation")`, sliding window 10, 50% failure rate threshold, 5s wait duration, RFC 7807 503 ProblemDetail), timeout policies (`TimeLimiter` 5s), inactive limiter eviction, and Micrometer/Prometheus metrics integration.

### 8C — Caching (Redis) ✅
Cache-aside pattern with profile-conditional caching: Redis in `prod` with modern `RedisSerializer.json()`, string keys, configurable TTLs (10m default/users, 1m user_ledgers), Caffeine in `!prod` (`local`/`test`). `@Cacheable` on user lookups and ledger queries with null-safe conditions, `@CacheEvict(allEntries = true)` on `UserService.registerUser()` and `TransactionService.sendMoney()`. Entity serialization hardening with lazy relation exclusions. Comprehensive slice & integration test coverage (110 passing tests).

### 8D — Distributed Locking (Redis / Redisson) ✅
Redisson 4.7.0 distributed locking (`payflow:lock:idemp:<key>`) for multi-instance idempotency coordination, fail-safe automatic lease expiration (10s), bounded wait time (2s) for cached replay resolution, safe thread-bound release (`isHeldByCurrentThread()`), and `NoOpDistributedLockService` fallback for zero-dependency non-prod profiles (`local`, `test`, `prod-light`). (125 passing tests).

---

## Phase 9 — Event Streaming ⬜ (Next Up)

### 9A — Kafka via Spring Modulith Event Externalization ⬜
Spring Modulith `spring-modulith-events-kafka` auto-externalizes domain events to Kafka topics with zero domain code changes. Kafka producer (`acks=all`, idempotent), Testcontainers Kafka tests. Local/test profiles remain in-process.

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
