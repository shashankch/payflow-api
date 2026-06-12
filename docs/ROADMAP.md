# Payflow API — Roadmap

This roadmap outlines a structured, phased progression to evolve the Payflow API from a phase 0 baseline into a highly resilient, enterprise-grade transactional backend.

Each major phase is subdivided into two iterative sub-phases (A and B) to support manageable implementation steps.

---

## Phase 0 — Baseline implementation (Current Status)

*Goal: Establish a running transactional baseline.*

### Deliverables
- Basic user and transaction API endpoints.
- In-memory H2 persistence for zero-dependency development builds.
- Layered service/repository architecture.
- Baseline Spring profiles setup (`local`).

---

## Phase 1 — Database & ACID Hardening

### Phase 1A — Schema Migration & Persistence Foundation
*Goal: Replace volatile H2 memory DB with PostgreSQL and optimize Hibernate queries.*

#### Work Items
- **Dependency Integration**: Add PostgreSQL driver and Flyway migration core dependencies to `pom.xml`.
- **Flyway Database Migrations**: Create initial schema SQL migrations (`V1__init_schema.sql` for users and transactions tables) inside `src/main/resources/db/migration`.
- **Integration Testing (Testcontainers)**: Setup Testcontainers to dynamically boot isolated PostgreSQL containers during the Maven test phase.
- **Spring Profile Configuration**: Ensure distinct configs:
  - `local`: Point to H2 or a local Postgres container.
  - `test`: Configure dynamic testcontainers JDBC URLs.
  - `prod`: Externalize credentials using environment variables.
- **JPA N+1 Resolution**: Configure `@EntityGraph` or explicit JPQL `JOIN FETCH` queries on repositories to ensure related transaction collections are resolved in a single SQL join rather than N+1 queries.

#### Acceptance Criteria
- Running `mvn clean test` boots a real PostgreSQL instance via Testcontainers and successfully executes tests.
- Flyway migrations apply cleanly upon application startup in all profiles.
- Hibernate query execution logs show a single query instead of N+1 SELECT statements when retrieving a user's transaction list.

### Phase 1B — Concurrency Control & Idempotency Engine
*Goal: Guarantee ledger consistency under race conditions and network retry duplication.*

#### Work Items
- **Pessimistic Row Locking**: Add `@Lock(LockModeType.PESSIMISTIC_WRITE)` to query methods in `UserRepository` for sender and receiver lookup.
- **Transactional Atomicity**: Configure `@Transactional(isolation = Isolation.READ_COMMITTED)` across services, sorting lock acquisition alphabetically by UPI ID to avoid deadlocks.
- **Persistent Idempotency Registry**:
  - Write Flyway migration for `idempotency_registry` table.
  - Implement an idempotency validator interceptor calculating request payload hashes (SHA-256).
  - Implement logic to intercept duplicate keys, serving cached responses for completed actions and rejecting active in-flight duplicates (`409 Conflict`).

#### Acceptance Criteria
- Concurrent requests seeking to transfer balance from the same source user concurrently fail gracefully, maintaining positive balances.
- Duplicate POST requests using the same `Idempotency-Key` header return cached results without executing the transaction twice.

---

## Phase 2 — Platform Security & Resilience

### Phase 2A — JWT Authentication & Security
*Goal: Restrict mutation endpoints to authenticated actors and establish API versioning.*

#### Work Items
- **API Versioning**: Map all controllers under versioned URL paths (e.g. `@RequestMapping("/api/v1/...")`), establishing a clear route namespace.
- **Spring Security Configuration**: Introduce Spring Security and block anonymous writes.
- **Stateless JWT Validation**: Create filters parsing Authorization headers and checking cryptographically signed JWT keys.
- **Secure Endpoints**: Restrict `POST /api/v1/transactions` so a user can only initiate transfers where the sender UPI matches their authenticated subject claim.

#### Acceptance Criteria
- Requests without a valid `Bearer JWT` token to transactions endpoints return `401 Unauthorized`.
- Authenticated users cannot trigger transfers masquerading as other users.
- Old endpoints are gracefully routed and version prefixes are strictly enforced.

### Phase 2B — Distributed Caching & Resilience Policies
*Goal: Optimize query pathways, prevent OOMs on free instances, and implement distributed locks.*

#### Work Items
- **Redis Integration**: Add Redis dependencies and configure a local connection pool.
- **Cache-Aside Pattern**: Cache user profiles by UPI ID on read requests (`GET /api/v1/users/upi/{upiId}`), with automatic cache eviction/invalidation on user updates.
- **Redis Distributed Locking**: Integrate Redisson for cross-instance locking. Apply a Redis-backed lock on the `Idempotency-Key` at the gateway layer to prevent multiple servers from concurrently starting database transactions on the same key.
- **Resilience4j Policies**:
  - Configure rate-limiting (Token Bucket) on transaction endpoints.
  - Apply call timeouts on database locks and external integrations.
  - Implement call retries with exponential backoff and random jitter for outbound message attempts.

#### Acceptance Criteria
- Reading a user profile by UPI hits the database once; subsequent calls load the profile from Redis.
- Gateway logs show concurrent duplicate requests hitting different nodes block on the Redis lock, preventing concurrent DB inserts.
- Flooding the transactions endpoint beyond rate-limit constraints returns `429 Too Many Requests`.

---

## Phase 3 — Event-Driven Telemetry & Orchestration

### Phase 3A — Asynchronous Streaming (Kafka & Outbox)
*Goal: Broadcast transaction events consistently without distributed dual-write bugs.*

#### Work Items
- **Transactional Outbox Table**: Write Flyway migration creating the `outbox_events` registry table.
- **Atomicity Integration**: Modify transaction services to write the event to the outbox table inside the same balance-updating database transaction.
- **Scheduled Dispatcher**: Implement a lightweight scheduled background worker reading `PENDING` outbox logs, publishing them to a Kafka broker, and updating their status to `DISPATCHED`.
- **Kafka Local Setup**: Integrate local Kafka configs utilizing KRaft mode (ZooKeeper-less setup).

#### Acceptance Criteria
- When a transaction commits, an outbox entry is atomically written.
- The background poller successfully dispatches the outbox event payload to the target Kafka topic.

### Phase 3B — Local Ecosystem & Observability
*Goal: Run the entire stack locally with telemetry monitoring.*

#### Work Items
- **Docker Compose Orchestration**: Develop a `docker-compose.yml` that maps the Spring Boot application container, PostgreSQL, Redis, Kafka, Prometheus, and Grafana.
- **Log Correlation (MDC)**: Update Logback patterns to inject Trace IDs and Span IDs into all standard console/file logs.
- **Prometheus Metrics**: Expose custom Micrometer gauges for successful payment counts, latency, and DB pool saturation to the `/actuator/prometheus` endpoint.

#### Acceptance Criteria
- Running `docker-compose up` launches all services cleanly.
- Logs include uniform trace metadata, and metrics are queryable inside the local Prometheus server.

---

## Phase 4 — AI Integrations, CI/CD, & Cloud

### Phase 4A — Gen-AI Spend Insights & CI/CD Pipelines
*Goal: Enforce build quality and integrate LLM spend analytics.*

#### Work Items
- **Spring AI Integration**: Add Spring AI dependencies to `pom.xml` configured for the selected LLM provider.
- **AI Spend Categorization**: Implement `POST /api/v1/transactions/{id}/insights` to send transaction notes and values to the LLM provider, requesting spend classification and structured budgeting insights using a custom system prompt.
- **GitHub Actions Pipeline**: Create a workflow triggered on commits and pull requests that compiles code, runs Checkstyle, executes SpotBugs vulnerability linter, and runs tests.

#### Acceptance Criteria
- The AI insights endpoint returns a structured JSON payload containing spend categories (e.g. `Transport`, `Food`) and budget advice from the AI model.
- Code changes cannot merge without passing checkstyle rules, SpotBugs analysis, and all integration tests in the GitHub Actions runner.

### Phase 4B — Kubernetes & Cloud Orchestration
*Goal: Ensure seamless migration to production cloud clusters.*

#### Work Items
- **Kubernetes Manifests**: Develop native YAML configurations:
  - `Deployment` (with CPU/Memory resources, liveness/readiness probes, and graceful shutdown settings).
  - `Service` (ClusterIP).
  - `ConfigMap` & `Secret` (decoupling environment variables).
- **Graceful Pod Lifecycle**: Configure Spring properties to allow graceful request drains (`server.shutdown=graceful`) mapping to Pod execution properties.
- **IaC Architectural Guidelines**: Document Terraform templates and AWS deployment guidelines (managed database, cache, event broker, and container orchestration services) in the architecture guidelines.

#### Acceptance Criteria
- Applying the manifests to a local Kubernetes namespace (Minikube/Kind) deploys the application with fully functioning health checks and environment mapping.
- Graceful shutdown handles in-flight transaction threads before pod termination.
