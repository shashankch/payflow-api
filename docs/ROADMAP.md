# Payflow API — Roadmap

This roadmap outlines a structured, phased progression to evolve the Payflow API from a Phase 0 baseline into a highly resilient, enterprise-grade transactional backend.

Each major phase is subdivided into iterative sub-phases scoped to a **single pull request**. Sub-phases are designed to be independently mergeable, buildable, and testable. Every sub-phase PR includes relevant documentation updates, diagrams, and testing to maintain a green build at all times.

### Design Principles
- **Free-tier first**: All infrastructure defaults to free, open-source options. Paid services are activated via profile-driven configuration changes only.
- **Documentation parity**: Every documented feature has corresponding implemented and tested code. No aspirational claims.
- **PR-sized increments**: Each sub-phase is a single, reviewable pull request.

### Status Legend
- ✅ Complete
- 🔄 In Progress
- ⬜ Not Started

---

## Phase 0 — Baseline Implementation ✅

*Goal: Establish a running transactional baseline.*

### Deliverables
- Basic user and transaction API endpoints.
- In-memory H2 persistence for zero-dependency development builds.
- Layered service/repository architecture.
- Baseline Spring profiles setup (`local`).

---

## Phase 1 — Project Hygiene & Dev Tooling ✅

*Goal: Establish professional project scaffolding, code quality enforcement, and continuous integration before any feature work begins.*

### Phase 1A — Project Scaffolding & Repository Standards ✅

#### Work Items
- Add MIT license file and initialize a changelog following Keep a Changelog format.
- Add `.editorconfig` enforcing consistent formatting rules across IDEs.
- Create Architecture Decision Records (ADR) log to document key technical decisions.
- Create engineering conventions document (coding standards, commit message format, PR guidelines).
- Add GitHub badges to README (CI status, Java version, Spring Boot version, license, code coverage).
- Harden `.gitignore` for comprehensive IDE/OS artifact exclusion.

### Phase 1B — Code Formatting & Static Analysis ✅

#### Work Items
- Integrate Spotless plugin (Eclipse Java Format) for automated code formatting.
- Integrate Checkstyle plugin with a baseline rule configuration (`checkstyle.xml`).
- Format all existing source files and bind checks to the Maven validate phase.

### Phase 1C — GitHub Actions CI Pipeline ✅

#### Work Items
- Create a CI workflow triggered on push and pull request events.
- Configure JDK setup, dependency caching, and full Maven verify execution.
- Document branch protection recommendations in `CONTRIBUTING.md`.

---

## Phase 2 — Domain Model & API Contract Hardening ⬜

*Goal: Transform bare JPA entities into a properly modeled domain with validated DTOs, clean error handling, and versioned API routes.*

### Phase 2A — Entity Model Hardening ⬜

#### Work Items
- Replace `Double` with `BigDecimal` for all monetary fields (financial precision).
- Add audit timestamps, transaction status/type enums, and reference IDs.
- Adopt Lombok to eliminate boilerplate. Add JPA `@Version` for optimistic locking support.
- Refactor all field injection to constructor injection.

### Phase 2B — DTO Layer, Input Validation & API Documentation ⬜

#### Work Items
- Create request DTOs with Jakarta validation annotations and response DTOs using Java records.
- Update controllers to accept validated DTOs and return response objects (never raw entities).
- Establish `/api/v1/` versioned route prefix on all endpoints.
- Return correct HTTP status codes (`201 Created`, `404 Not Found`, etc.).
- Integrate `springdoc-openapi` for auto-generated interactive Swagger UI and OpenAPI 3.0 specification.

### Phase 2C — Error Handling & Exception Framework ⬜

#### Work Items
- Create domain-specific exception hierarchy (e.g., not found, insufficient balance, duplicate UPI, self-transfer).
- Implement a global exception handler using Spring's native RFC 7807 `ProblemDetail` support.
- Replace all `System.out.println` usage with SLF4J structured logging.

---

## Phase 3 — ACID Transaction Logic & Concurrency Control ⬜

*Goal: Implement real money transfer mechanics with ledger integrity guarantees under concurrent load.*

### Phase 3A — Money Transfer & Refund Implementation ⬜

#### Work Items
- Implement end-to-end transfer orchestration: validate participants, check balance, debit/credit, persist transaction record — all within a single `@Transactional` boundary.
- Implement transaction refund/reversal endpoint following append-only ledger semantics.
- Add transaction status lookup and paginated user transaction history endpoints.

### Phase 3B — Pessimistic Locking & Deadlock Avoidance ⬜

#### Work Items
- Add pessimistic write lock (`SELECT ... FOR UPDATE`) on balance lookups during transfers.
- Implement deterministic lock ordering (alphabetical UPI ID sort) to prevent deadlock cycles.
- Enforce append-only ledger semantics on the transactions table.
- Resolve JPA N+1 query patterns using `@EntityGraph` and explicit fetch joins.

---

## Phase 4 — Database Migration & Profile Infrastructure ⬜

*Goal: Replace volatile H2 with PostgreSQL, establish Flyway-managed schema evolution, and configure environment-specific profiles.*

### Phase 4A — Flyway Migrations & PostgreSQL Integration ⬜

#### Work Items
- Add PostgreSQL driver and Flyway dependencies.
- Create versioned SQL migration scripts for users, transactions, and performance indexes.
- Restructure configuration from `.properties` to YAML format.

### Phase 4B — Spring Profiles & Testcontainers Setup ⬜

#### Work Items
- Create profile-specific configuration files (`local`, `test`, `prod`).
- Add Testcontainers dependencies and create a shared integration test base class.
- Enforce Flyway-only schema control in production (`ddl-auto=validate`).

---

## Phase 5 — Testing ⬜

*Goal: Establish comprehensive test coverage with unit, integration, and concurrency verification.*

### Phase 5A — Unit Tests ⬜

#### Work Items
- Service layer tests using Mockito: transfer happy path, edge cases, refund scenarios.
- Controller layer tests using `@WebMvcTest` + MockMVC: input validation, status codes, error payloads.
- Repository layer tests using `@DataJpaTest`: custom query verification.

### Phase 5B — Integration & Concurrency Tests ⬜

#### Work Items
- Full transfer lifecycle tests against Testcontainers PostgreSQL.
- Concurrent race condition test: 10 simultaneous threads contending on the same balance, verifying exactly 1 succeeds and no double-spend occurs.
- Update CI pipeline to run integration tests.

---

## Phase 6 — Idempotency & Transactional Outbox ⬜

*Goal: Guarantee exactly-once mutation execution and reliable event streaming without dual-write vulnerabilities.*

### Phase 6A — Durable Idempotency Engine ⬜

#### Work Items
- Create idempotency registry schema (Flyway migration), entity, and repository.
- Implement a request filter that intercepts `Idempotency-Key` headers, computes payload hashes (SHA-256), and replays cached responses for duplicate requests.
- Handle concurrent duplicate submissions with `409 Conflict`.

### Phase 6B — Transactional Outbox Pattern ⬜

#### Work Items
- Create outbox events schema (Flyway migration), entity, and repository.
- Write outbox events atomically within the same transaction as balance updates.
- Implement a scheduled dispatcher that polls and publishes pending events.
- Define a pluggable event publisher interface with an in-memory default implementation.

---

## Phase 7 — Security & Access Control ⬜

*Goal: Restrict mutation endpoints to authenticated actors using stateless JWT validation.*

### Phase 7A — Spring Security & JWT Authentication ⬜

#### Work Items
- Integrate Spring Security with stateless JWT-based authentication.
- Create a JWT token provider and authentication filter.
- Configure endpoint security: permit public registration, authenticate transaction mutations.
- Configure CORS policy for frontend integration readiness.
- Add a simplified auth/login endpoint for token issuance.

### Phase 7B — Authorization & Sender Verification ⬜

#### Work Items
- Verify that the authenticated user's identity matches the sender in transfer requests (`403 Forbidden` on mismatch).
- Restrict transaction history access to the owning user.
- Add comprehensive security-focused unit tests.

---

## Phase 8 — Observability, Resilience, Caching & Event Streaming ⬜

*Goal: Add structured telemetry, fault tolerance policies, distributed caching, and production event streaming.*

### Phase 8A — Structured Logging, Tracing & Prometheus Metrics ⬜

#### Work Items
- Configure JSON-structured logging with MDC trace correlation (traceId, spanId, requestId).
- Add distributed tracing support with Micrometer Tracing for cross-service trace propagation.
- Expose custom Micrometer counters for payment volumes, failure rates, and amount distributions.
- Harden actuator endpoint exposure.

### Phase 8B — Resilience4j Policies ⬜

#### Work Items
- Add rate limiting (token bucket) on transaction endpoints with `429 Too Many Requests`.
- Add retry with exponential backoff and jitter on outbox event dispatch.
- Add timeout policies on lock acquisition and external calls.
- Export resilience metrics to Prometheus.

### Phase 8C — Distributed Caching & Locking (Redis) ⬜

#### Work Items
- Integrate Redis with Spring Cache abstraction for cache-aside pattern on user lookups.
- Automatic cache eviction on writes with `@CacheEvict`.
- Enhance idempotency filter with Redis-backed distributed locking (Redisson) for multi-instance gateway coordination.
- Profile-conditional bean wiring: Redis for `prod`, Caffeine for `local`/`test`.
- Add cache hit/miss metrics to Prometheus.

### Phase 8D — Kafka Integration & Event Streaming ⬜

#### Work Items
- Implement a Kafka-backed event publisher (activated in `prod` profile).
- Configure Kafka producer with durability guarantees (`acks=all`).
- Add Testcontainers Kafka integration tests for end-to-end event verification.

---

## Phase 9 — Infrastructure, Docker & Deployment ⬜

*Goal: Production-grade containerization, local full-stack orchestration, Kubernetes readiness, and cloud deployment.*

### Phase 9A — Multi-Stage Dockerfile & Docker Compose ⬜

#### Work Items
- Rewrite Dockerfile with multi-stage build, correct JDK version, non-root user, and health check.
- Expand Docker Compose to orchestrate: application, PostgreSQL, Redis, Kafka, Prometheus, and Grafana.
- Add monitoring configuration (Prometheus scrape config, Grafana provisioned dashboards).

### Phase 9B — Kubernetes Manifests ⬜

#### Work Items
- Create Deployment, Service, ConfigMap, and Secret manifests.
- Configure liveness/readiness probes and graceful shutdown alignment.
- Validate deployment on a local Minikube/Kind cluster.

### Phase 9C — CI/CD Enhancement & Code Coverage ⬜

#### Work Items
- Add SpotBugs static analysis, artifact archiving, and dependency caching to the CI workflow.
- Integrate JaCoCo for code coverage reporting with minimum threshold enforcement.
- Add CI status and code coverage badges to README.

---

## Phase 10 — Gen-AI Spend Insights ⬜

*Goal: Integrate LLM-powered transaction categorization and budgeting insights.*

### Phase 10A — Spring AI Integration & Categorization Endpoint ⬜

#### Work Items
- Integrate Spring AI with a provider-agnostic abstraction (Ollama for local, Groq/Gemini for deployed).
- Implement a spend insights service with structured prompt engineering and JSON-schema output parsing.
- Add authenticated insights endpoint with circuit breaker fallback.
- Feature-flag AI capabilities for environments without API keys.

---

## Phase 11 — Production Hardening & Documentation ⬜

*Goal: Final polish for production readiness, cloud deployment, and professional presentation.*

### Phase 11A — prod-light Profile, Virtual Threads & GraalVM ⬜

#### Work Items
- Create a memory-optimized profile for resource-constrained deployments (1 GiB RAM).
- Swap Redis/Kafka with lightweight in-memory alternatives using conditional bean wiring.
- Enable virtual threads (`spring.threads.virtual.enabled=true`) and tune HikariCP pool sizing.
- Validate and document GraalVM native compilation (`mvn -Pnative native:compile`).

### Phase 11B — Cloud Deployment & Documentation Finalization ⬜

#### Work Items
- Document cloud deployment options with step-by-step guides for AWS Free Tier and Oracle Cloud Always Free.
- Ensure all documentation accurately reflects implemented, tested features.
- Finalize architecture docs with Mermaid diagrams, API specification, ADR log, changelog, and README.
- Validate that a new developer can clone and run the project within 5 minutes.
