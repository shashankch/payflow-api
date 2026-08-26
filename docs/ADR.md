# Payflow API — Architecture Decision Records (ADRs)

This document serves as the log of all architectural decisions made during the evolution of the Payflow API payment backend.

ADRs document significant technical decisions, along with their context, rationale, trade-offs, and consequences.

---

## ADR Index

| ADR # | Title | Date | Status |
| :--- | :--- | :--- | :--- |
| [ADR-001](#adr-001-use-bigdecimal-for-all-monetary-values) | Use BigDecimal for all monetary values | 2026-08-01 | Accepted |
| [ADR-002](#adr-002-constructor-injection-over-field-injection) | Constructor injection over field injection | 2026-08-01 | Accepted |
| [ADR-003](#adr-003-rich-domain-model-over-anemic-domain-model) | Rich Domain Model over Anemic Domain Model | 2026-08-01 | Accepted |
| [ADR-004](#adr-004-uri-based-api-versioning-and-dto-isolation-layer) | URI-based API Versioning and DTO Isolation Layer | 2026-08-01 | Accepted |
| [ADR-005](#adr-005-mapstruct-for-compile-time-type-safe-dto-mapping) | MapStruct for compile-time type-safe DTO mapping | 2026-08-02 | Accepted |
| [ADR-006](#adr-006-rfc-7807-problemdetail--centralized-exception-handling) | RFC 7807 ProblemDetail & Centralized Exception Handling | 2026-08-03 | Accepted |
| [ADR-007](#adr-007-uuid-reference-ids-over-auto-increment-primary-keys) | UUID Reference IDs over Auto-Increment Primary Keys | 2026-08-05 | Accepted |
| [ADR-008](#adr-008-spring-modulith-modular-monolith-over-distributed-microservices) | Spring Modulith Modular Monolith over Distributed Microservices | 2026-08-07 | Accepted |
| [ADR-009](#adr-009-opentelemetry-distributed-tracing-via-micrometer-bridge) | OpenTelemetry Distributed Tracing via Micrometer Bridge | 2026-08-07 | Accepted |
| [ADR-010](#adr-010-pessimistic-locking-for-high-concurrency-balance-operations) | Pessimistic Locking for High-Concurrency Balance Operations | 2026-08-07 | Accepted |
| [ADR-011](#adr-011-deterministic-lock-ordering-for-deadlock-prevention) | Deterministic Lock Ordering for Deadlock Prevention | 2026-08-07 | Accepted |
| [ADR-012](#adr-012-double-entry-balance-ledger-as-immutable-audit-trail) | Double-Entry Balance Ledger as Immutable Audit Trail | 2026-08-09 | Accepted |
| [ADR-013](#adr-013-flyway-database-migrations-over-ddl-auto-generation) | Flyway Database Migrations over DDL Auto-Generation | 2026-08-10 | Accepted |
| [ADR-014](#adr-014-spring-environment-profiles-and-testcontainers-integration-testing-strategy) | Spring Environment Profiles and Testcontainers Integration Testing Strategy | 2026-08-11 | Accepted |
| [ADR-015](#adr-015-sha-256-request-payload-hashing--durable-database-backed-idempotency-engine) | SHA-256 Request Payload Hashing & Durable Database-Backed Idempotency Engine | 2026-08-15 | Accepted |
| [ADR-016](#adr-016-spring-modulith-event-publication-registry--transactional-outbox-pattern) | Spring Modulith Event Publication Registry & Transactional Outbox Pattern | 2026-08-16 | Accepted |
| [ADR-017](#adr-017-stateless-jwt-authentication--spring-security-architecture) | Stateless JWT Authentication & Spring Security Architecture | 2026-08-17 | Accepted |
| [ADR-018](#adr-018-principal-bound-resource-access-control--sender-verification) | Principal-Bound Resource Access Control & Sender Verification | 2026-08-18 | Accepted |
| [ADR-019](#adr-019-declarative-http-interface-client-restclient--resilient-external-service-integration) | Declarative HTTP Interface Client (RestClient) & Resilient External Service Integration | 2026-08-26 | Accepted |

---

### ADR-001: Use BigDecimal for all monetary values

**Date**: 2026-08-01  
**Status**: Accepted  
**Phase**: Phase 2A  

#### Context & Problem Statement
Floating-point primitive types (`double`, `float`) use IEEE 754 binary representation, which cannot precisely represent base-10 decimals (e.g., `0.1 + 0.2 = 0.30000000000000004`). In financial applications, accumulated rounding errors compromise ledger integrity and result in monetary discrepancy.

#### Considered Options
1. **IEEE 754 Floating-Point (`Double`/`float`)**: Fast, built-in, but causes imprecise rounding error.
2. **Integer Cent/Sub-unit Amounts (`Long` cents)**: Precise, but awkward when dealing with fractional currency fractions or dynamic currency precision.
3. **Java `BigDecimal`**: Arbitrary-precision signed decimal numbers, explicitly suited for financial calculations.

#### Decision Outcome
Chosen Option: **Java `BigDecimal`**

##### Rationale
`BigDecimal` provides exact precision representation for decimal currency values with configurable scale (`precision = 19, scale = 4` in database mapping) and explicit rounding modes (`RoundingMode.HALF_EVEN`).

#### Consequences
- **Positive**: Zero floating-point rounding errors in monetary balance arithmetic and ledger entries.
- **Negative / Trade-offs**: Slightly higher memory overhead and minor performance cost compared to native double primitives.
- **Risks & Mitigations**: Always specify scale and explicit rounding mode (`RoundingMode.HALF_EVEN`) when performing division or scale adjustments.

---

### ADR-002: Constructor injection over field injection

**Date**: 2026-08-01  
**Status**: Accepted  
**Phase**: Phase 2A  

#### Context & Problem Statement
Field injection via `@Autowired` tightly couples Spring components to the Spring DI container, prevents `final` immutable fields, hides component dependencies, and makes unit testing difficult without starting Spring contexts or using reflection.

#### Considered Options
1. **Field Injection (`@Autowired private Service service`)**: Convenient syntax, but hides dependencies and inhibits immutability/testability.
2. **Setter Injection**: Allows optional dependencies, but enables mutable component state post-construction.
3. **Constructor Injection**: Explicitly declares required dependencies as `final` parameters.

#### Decision Outcome
Chosen Option: **Constructor Injection**

##### Rationale
Constructor injection enforces immutability (`final` fields), guarantees all required dependencies are provided at instantiation time, simplifies unit testing without Spring context spinners, and aligns with Spring Framework best practices.

#### Consequences
- **Positive**: Clean component testability, immutability guarantees, static dependency verification at compile time.
- **Negative / Trade-offs**: Slightly more boilerplate constructor code (mitigated by explicit standard constructors).
- **Risks & Mitigations**: Circular dependency detection occurs at startup (which is desirable as it indicates architectural smell).

---

### ADR-003: Rich Domain Model over Anemic Domain Model

**Date**: 2026-08-01  
**Status**: Accepted  
**Phase**: Phase 2A  

#### Context & Problem Statement
Anemic domain models treat JPA entities as simple data bags with getters and setters, scattering business invariants (such as non-negative balance checks and debit rules) across multiple service classes.

#### Considered Options
1. **Anemic Domain Model**: Entities hold only state; services perform all business logic and validations.
2. **Rich Domain Model**: Entities encapsulate state alongside domain behaviors and invariant checks (`debit()`, `credit()`).

#### Decision Outcome
Chosen Option: **Rich Domain Model**

##### Rationale
Rich domain entities encapsulate domain invariants directly within entity boundaries (`User.debit()`, `User.credit()`), preventing invalid domain state transitions (e.g. negative balances or invalid debit amounts) regardless of caller invocation path.

#### Consequences
- **Positive**: High cohesion, self-validating entities, reusable business logic across multiple services.
- **Negative / Trade-offs**: Entities must remain decoupled from infrastructure concerns (repositories, external APIs).
- **Risks & Mitigations**: Keep entity methods focused strictly on state invariants; delegate orchestration to domain services.

---

### ADR-004: URI-based API Versioning and DTO Isolation Layer

**Date**: 2026-08-01  
**Status**: Accepted  
**Phase**: Phase 2B  

#### Context & Problem Statement
Exposing JPA entities directly through REST controllers risks over-posting, entity leak, tight coupling of API contracts to database schemas, and backwards-incompatible API breaks whenever internal domain models evolve. Additionally, unversioned API endpoints make contract evolution hazardous for clients.

#### Considered Options
1. **Direct JPA Entity Exposure (Unversioned)**: Simple initial implementation, but leaks database structure, enables mass assignment vulnerabilities, and lacks contract stability.
2. **Header or Parameter Versioning**: Flexible versioning via HTTP headers (`Accept: application/vnd.payflow.v1+json`), but harder to cache via HTTP reverse proxies and less clear in logs.
3. **Explicit URI Path Versioning (`/api/v1/`) with DTO Isolation**: Explicit `/api/v1/` prefix with dedicated request objects (Java classes with Jakarta Validation) and response records (`UserResponse`, `TransactionResponse`, `PagedResponse`).

#### Decision Outcome
Chosen Option: **URI Path Versioning (`/api/v1/`) with DTO Isolation**

##### Rationale
- **Contract Stability**: Isolates REST API contracts from internal JPA entities using Java records for responses and validated DTO classes for requests.
- **Security & Validation**: Enforces exact field constraints (`@NotBlank`, `@Pattern`, `@DecimalMin`, `@Max`) at the API entry point via Jakarta Validation (`@Valid`), preventing malformed requests from reaching business logic.
- **Observability & Caching**: URI versioning (`/api/v1/`) provides clean HTTP proxy caching and transparent log routing across API gateway boundaries.

#### Consequences
- **Positive**: Strict decoupling between database tables and API responses; robust validation; backward compatibility pathway (`/api/v2/` in future phases).
- **Negative / Trade-offs**: Mapping code required between DTOs and entities (`UserResponse.fromEntity()`).
- **Risks & Mitigations**: Maintain mapping logic in static factory methods on response records to keep controllers clean and performant.

---

### ADR-005: MapStruct for compile-time type-safe DTO mapping

**Date**: 2026-08-02  
**Status**: Accepted  
**Phase**: Phase 2C  

#### Context & Problem Statement
Manual object mapping between entities and DTOs introduces boilerplate code, increases maintenance overhead as domain models expand, and is prone to human error (such as missed field copies). Runtime reflection mapping frameworks (e.g. ModelMapper) introduce non-trivial performance latency and hide field type mismatches until runtime execution.

#### Considered Options
1. **Manual Mapping Methods**: Writing custom `toEntity()` / `toResponse()` conversion code in controllers or static factory methods. Highly performant, but repetitive and boilerplate-heavy.
2. **Runtime Reflection Mappers (ModelMapper, Dozer)**: Automated mapping via reflection, but incurs runtime CPU overhead and hides mapping errors until runtime execution.
3. **Compile-Time Code Generation (MapStruct)**: Annotation processor generates plain, un-reflected Java byte-code at compile time with type checking and zero runtime performance penalty.

#### Decision Outcome
Chosen Option: **MapStruct (Compile-Time Generation)**

##### Rationale
- **Zero Runtime Overhead**: MapStruct generates plain Java method calls during compilation; no reflection is executed at runtime.
- **Compile-Time Verification**: Unmapped target properties or mismatched types raise immediate compiler errors rather than silent runtime failures.
- **Spring Integration**: Native integration with Spring Dependency Injection (`componentModel = "spring"`), allowing mappers to be injected clean into `@RestController` components.

#### Consequences
- **Positive**: Blazing-fast performance (identical to handwritten code), strict compile-time type checking, clean controller code.
- **Negative / Trade-offs**: Requires annotation processor configuration (`mapstruct-processor`) in Maven `pom.xml`.
- **Risks & Mitigations**: Ensure `maven-compiler-plugin` includes `mapstruct-processor` in its `annotationProcessorPaths`.

---

### ADR-006: RFC 7807 ProblemDetail & Centralized Exception Handling

**Date**: 2026-08-03  
**Status**: Accepted  
**Phase**: Phase 2D  

#### Context & Problem Statement
Without centralized exception handling, uncaught runtime exceptions return default Spring Whitelabel 500 HTML pages or raw Java stack traces, leaking internal system implementation details, security vulnerabilities, and database query structures. Furthermore, non-standardized error JSON responses force frontends and API consumers to write ad-hoc error handling logic for different endpoints.

#### Considered Options
1. **Default Spring Boot Whitelabel / ErrorController**: Simple, but exposes HTML or unstandardized JSON responses without consistent error schema.
2. **Custom DTO Error Response Class**: Custom Java class. Standardized within the application, but non-compliant with open web standards.
3. **RFC 7807 `ProblemDetail` via `@RestControllerAdvice`**: Native Spring Boot standard (`org.springframework.http.ProblemDetail`) defining structured HTTP error responses with `type`, `title`, `status`, `detail`, `instance`, `timestamp`, and `X-Request-Id` correlation tracking.

#### Decision Outcome
Chosen Option: **RFC 7807 `ProblemDetail` via `@RestControllerAdvice`**

##### Rationale
- **Industry Standard**: RFC 7807 provides a universally recognized format (`application/problem+json`) understood natively by modern API clients and gateways.
- **Zero Information Leakage**: All uncaught exceptions are intercepted and sanitized to generic 500 responses (`"An unexpected internal error occurred"`), preventing stack trace leaks.
- **Field-Level Validation Reporting**: DTO validation errors (`MethodArgumentNotValidException`) provide structured `errors` maps with status `422 Unprocessable Entity`.
- **Request Correlation**: Integrated with `RequestIdFilter` (`X-Request-Id` in MDC) so every error response includes the request correlation ID for distributed tracing.

#### Consequences
- **Positive**: Consistent API error contract, enhanced security, production-grade observability and correlation tracing.
- **Negative / Trade-offs**: Custom exceptions must be mapped in `@RestControllerAdvice`.
- **Risks & Mitigations**: Ensure all domain services throw specific `PayflowException` subtypes rather than generic runtime exceptions.

---

### ADR-007: UUID Reference IDs over Auto-Increment Primary Keys in APIs

**Date**: 2026-08-05  
**Status**: Accepted  
**Phase**: Phase 2E  

#### Context & Problem Statement
Exposing auto-increment database primary keys (`Long userId`) in external REST URLs (e.g. `/api/v1/users/1`) introduces significant security vulnerabilities:
1. **Resource Enumeration Attacks**: Attackers can sequentially query `/users/1`, `/users/2`, `/users/3` to scrape all system user profiles.
2. **Business Metric Leakage**: Competitors can determine total registered user growth rates by observing sequential ID progression over time.
3. **Internal Key Coupling**: Exposing internal database sequence keys couples external client contracts directly to database storage strategies.

#### Considered Options
1. **Expose Auto-Increment Long Primary Keys (`userId`)**: Simple, but vulnerable to enumeration attacks and leaks business growth metrics.
2. **Expose Friendly Handles Only (`upiId`)**: Human-readable (`shashank@kotak`), but handles can change over time as users re-link bank accounts.
3. **Dual Identification Strategy (Internal `userId`, External `referenceId` UUID)**: Retain `Long userId` internally for fast database foreign key joins and indexing, but assign a non-enumerable `UUID referenceId` for all API responses and URL routing.

#### Decision Outcome
Chosen Option: **Dual Identification Strategy (Internal `userId`, External `referenceId` UUID)**

##### Rationale
- **Security & Privacy**: Cryptographically pseudo-random UUID v4 strings prevent resource enumeration attacks and hide user creation counts.
- **Domain Flexibility**: `upiId` remains the friendly human-readable handle (`shashank@kotak`), while `referenceId` serves as the immutable internal system reference.
- **Performance**: High-performance SQL joins continue to utilize numeric `BIGINT` primary/foreign keys (`user_id`), avoiding string join performance overhead in PostgreSQL.

#### Consequences
- **Positive**: Complete insulation against resource enumeration attacks, non-leaky API contracts, optimized database joins.
- **Negative / Trade-offs**: Entities require a `@PrePersist` hook or column default to generate UUIDs upon creation.
- **Risks & Mitigations**: Ensure secondary unique index (`idx_users_reference_id`) is maintained on `referenceId`.

---

<<<<<<< Updated upstream
### ADR-008: Spring Modulith Modular Monolith over Distributed Microservices

**Date**: 2026-08-07  
**Status**: Accepted  
**Phase**: Phase 6B (Architecture Review)  

#### Context & Problem Statement
Payflow processes peer-to-peer financial transfers where a single operation involves debiting the sender, crediting the receiver, recording a transaction, writing ledger entries, and publishing domain events. Splitting these into separate microservices (e.g., `User Service`, `Transfer Service`, `Ledger Service`) would lose local database ACID transactions, requiring distributed saga orchestration or two-phase commit (2PC) to maintain consistency. This introduces significant complexity, dual-write bugs, and split-brain risks before the domain rules are even stable.

#### Considered Options
1. **Distributed Microservices (HTTP/gRPC)**: Each domain concern runs as a separate deployable service. Requires distributed transactions (sagas, 2PC) for cross-service state consistency.
2. **Plain Monolith (No Module Boundaries)**: Single deployable with no enforced package boundaries. Simple but leads to spaghetti coupling as the codebase grows.
3. **Spring Modulith Modular Monolith**: Single deployable with strict, compile-time-verified module boundaries. Uses Spring's `ApplicationEventPublisher` for inter-module communication. Evolves to Kafka event streaming via `spring-modulith-events-kafka` without domain code changes.

#### Decision Outcome
Chosen Option: **Spring Modulith Modular Monolith**

##### Rationale
- **ACID Safety**: Sender debit, receiver credit, transaction record, ledger entries, and domain event all execute in a single `@Transactional` database transaction. Zero distributed transaction overhead.
- **Enforced Boundaries**: `ApplicationModules.of(PayflowApiApplication.class).verify()` test validates strict package encapsulation at compile time, preventing accidental cross-module coupling.
- **Event Publication Registry**: Spring Modulith's `spring-modulith-starter-jpa` persists domain events (e.g., `TransferCompletedEvent`) to an `event_publication` table atomically within the same DB transaction. This is a framework-managed transactional outbox.
- **Evolutionary Path**: Adding `spring-modulith-events-kafka` in Phase 9A auto-externalizes events to Kafka topics with zero changes to `TransactionService` domain code. The service continues to call `applicationEventPublisher.publishEvent()` — the framework bridges to Kafka transparently.
- **Architectural Simplicity**: Avoids premature microservice distribution and preserves single-database transaction boundaries until physical service isolation is explicitly required.

#### Consequences
- **Positive**: Single-database ACID safety, zero distributed transaction complexity, compile-time boundary enforcement, seamless Kafka evolution path.
- **Negative / Trade-offs**: All modules share a single database and JVM. Horizontal scaling is per-application-instance, not per-module.
- **Risks & Mitigations**: If individual modules need independent scaling (unlikely at Payflow's scale), Spring Modulith modules can be extracted to standalone services along their already-enforced API boundaries.

---

### ADR-009: OpenTelemetry Distributed Tracing via Micrometer Bridge

**Date**: 2026-08-07  
**Status**: Accepted  
**Phase**: Phase 8A (Architecture Review)  

#### Context & Problem Statement
Payflow needs distributed tracing to correlate requests across HTTP controllers, database queries, async event listeners, and (in production) Kafka consumers. The existing `X-Request-Id` MDC pattern (Phase 2D) provides basic request correlation but does not follow W3C trace propagation standards, making it incompatible with industry-standard tracing backends (Grafana Tempo, Jaeger, Zipkin).

#### Considered Options
1. **Custom `X-Request-Id` only (Current State)**: Simple UUID injected via `RequestIdFilter`. No W3C standard compliance, no automatic span propagation across async boundaries.
2. **Vendor-Specific Tracing SDK (e.g., Datadog, New Relic)**: Proprietary SDKs with deep integration but vendor lock-in and paid tiers.
3. **Micrometer Tracing + OpenTelemetry Bridge**: Uses `micrometer-tracing-bridge-otel` to bridge Spring Boot's native Micrometer Observation API to the OpenTelemetry SDK. Exports traces via OTLP to any compatible backend. W3C `traceparent` propagation standard.

#### Decision Outcome
Chosen Option: **Micrometer Tracing + OpenTelemetry Bridge**

##### Rationale
- **W3C Standard**: `traceparent` headers (`traceId`, `spanId`) are automatically injected into HTTP requests, database queries, and async thread pools. Compatible with any OTLP backend.
- **Zero Vendor Lock-in**: Micrometer Tracing is the Spring Boot native abstraction. The OTel bridge can export to Grafana Tempo (free/open-source), Jaeger, Zipkin, or any commercial APM.
- **Coexistence with X-Request-Id**: The existing `RequestIdFilter` and MDC `requestId` are preserved. Both `requestId` and `traceId` appear in structured log output, providing layered correlation.
- **Automatic Instrumentation**: `@Observed` annotation on service methods (e.g., `sendMoney()`) creates spans with business-relevant names and tags automatically.
- **All Open-Source**: `micrometer-tracing-bridge-otel` and `opentelemetry-exporter-otlp` are fully open-source with no paid tiers.

#### Consequences
- **Positive**: Industry-standard distributed tracing, portable across backends, automatic span propagation, zero vendor lock-in.
- **Negative / Trade-offs**: Additional dependencies (`micrometer-tracing-bridge-otel`, `opentelemetry-exporter-otlp`). Trace sampling must be configured to avoid excessive overhead in production.
- **Risks & Mitigations**: Set `management.tracing.sampling.probability` to `1.0` for dev/staging (100% traces) and `0.1` for production (10% sampling) to balance observability with performance.
=======
### ADR-010: Pessimistic Locking for High-Concurrency Balance Operations

**Date**: 2026-08-07  
**Status**: Accepted  
**Phase**: Phase 3B  

#### Context & Problem Statement
Money transfers require atomic balance updates (`sender.debit()`, `receiver.credit()`). Under concurrent execution, read-then-write patterns without explicit database locking lead to race conditions and lost updates (double-spending).

#### Considered Options
1. **Optimistic Locking (`@Version`)**: Detects concurrent modifications at commit time and throws `OptimisticLockException`. Requires retry loops in application code. Under high write contention (e.g. popular merchants receiving hundreds of transfers per second), optimistic locking results in high abort rates.
2. **Pessimistic Write Locking (`SELECT ... FOR UPDATE`)**: Acquires exclusive row-level database locks when reading user balances inside the transaction boundary. Subsequent concurrent transactions attempting to read/lock the same account block until the holding transaction commits or rolls back.

#### Decision Outcome
Chosen Option: **Pessimistic Write Locking (`SELECT ... FOR UPDATE`)**

##### Rationale
- **Guaranteed Consistency**: Exclusive row locks prevent concurrent reads of stale balances during active transfers, eliminating double-spending race conditions.
- **Predictable Execution**: Transactions execute sequentially per account without triggering application-level retry loops or transaction abort spikes under high write contention.

#### Consequences
- **Positive**: Guaranteed ACID balance integrity, zero double-spend window.
- **Negative / Trade-offs**: Concurrent transfers targeting the same account wait on database row locks, increasing database connection hold times under load.
- **Risks & Mitigations**: Set explicit `@Transactional(timeout = 5)` transaction timeouts to prevent lock wait deadlocks from holding connection pool resources indefinitely.

---

### ADR-011: Deterministic Lock Ordering for Deadlock Prevention

**Date**: 2026-08-07  
**Status**: Accepted  
**Phase**: Phase 3B  

#### Context & Problem Statement
When acquiring pessimistic write locks on two database rows (sender and receiver accounts), non-deterministic lock acquisition order causes database deadlocks under concurrent cross-transfers (e.g. Tx 1: Alice sends to Bob; Tx 2: Bob sends to Alice). Tx 1 locks Alice then waits for Bob; Tx 2 locks Bob then waits for Alice, resulting in a cyclical lock dependency deadlock.

#### Considered Options
1. **Application Lock Ordering (Sender First, Receiver Second)**: Simple, but vulnerable to deadlocks whenever reciprocal transfers execute concurrently.
2. **Deterministic Lock Ordering (Alphabetical by UPI ID)**: Sort sender and receiver UPI IDs lexicographically prior to lock acquisition. Both Tx 1 and Tx 2 acquire locks in the exact same sequence (`alice@payflow` first, then `bob@payflow`).

#### Decision Outcome
Chosen Option: **Deterministic Lock Ordering (Alphabetical by UPI ID)**

##### Rationale
- **Deadlock Elimination**: Strictly ordering lock requests prevents cyclical wait graphs at the database level. Both reciprocal transfers attempt to lock `alice@payflow` first; the second transaction cleanly blocks until the first completes.
- **Zero Overhead**: Sorting two string references in memory takes negligible time (<1 microsecond).

#### Consequences
- **Positive**: Eliminates database deadlocks during reciprocal concurrent money transfers.
- **Negative / Trade-offs**: Requires minor mapping logic to re-assign `sender` and `receiver` domain entity references after acquiring locks in sorted order.

---

### ADR-012: Double-Entry Balance Ledger as Immutable Audit Trail

**Date**: 2026-08-09  
**Status**: Accepted  
**Phase**: Phase 3C  

#### Context & Problem Statement
Directly mutating `users.balance` without an explicit ledger record creates financial audit risks: if a balance value becomes corrupted or disputed, there is no immutable audit trail to reconstruct the historical sequence of balance states or verify financial integrity.

#### Considered Options
1. **Single Balance Field Mutation (`users.balance`)**: Simple, but lacks historical auditability and makes financial balance reconciliation impossible.
2. **Double-Entry Balance Ledger (`balance_ledger` table)**: Write two immutable ledger entries (`DEBIT` for sender, `CREDIT` for receiver) capturing `amount`, `balanceBefore`, and `balanceAfter` inside the same `@Transactional` database boundary as the money transfer.

#### Decision Outcome
Chosen Option: **Double-Entry Balance Ledger (`balance_ledger` table)**

##### Rationale
- **Financial Auditability**: Every money movement creates two immutable ledger entries documenting exact balance state changes before and after execution.
- **Reconciliation Support**: `users.balance` acts as a high-performance denormalized field; the actual source of financial truth can be reconstructed at any time using a `SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE -amount END)` SQL aggregate query over the `balance_ledger` table.
- **ACID Atomicity**: Sender debit, receiver credit, transaction record, and both ledger entries execute in a single database transaction.

#### Consequences
- **Positive**: Complete audit trail, balance reconstruction capability, financial compliance.
- **Negative / Trade-offs**: Increases database row writes per transaction from 3 to 5 (2 user updates, 1 transaction record, 2 ledger records).

---

### ADR-013: Flyway Database Migrations over DDL Auto-Generation

**Date**: 2026-08-10  
**Status**: Accepted  
**Phase**: Phase 4A  

#### Context & Problem Statement
Relying on Hibernate `spring.jpa.hibernate.ddl-auto=update` or `create-drop` in production environment creates high-risk database mutation vulnerabilities: schema changes are unversioned, non-repeatable, cannot be reviewed in pull requests, and risk accidental data loss or locking during application restarts.

#### Considered Options
1. **Hibernate DDL Auto-Generation (`ddl-auto=update`)**: Convenient for early prototyping, but non-deterministic, unversioned, and prohibited in production environments.
2. **Flyway Versioned SQL Migrations**: Versioned, immutable SQL migration scripts (`V1__...`, `V2__...`) stored in version control (`db/migration`), executed deterministically on startup, with Hibernate configured to `ddl-auto=validate`.

#### Decision Outcome
Chosen Option: **Flyway Versioned SQL Migrations (`org.flywaydb:flyway-core`)**

##### Rationale
- **Production Safety**: Schema migrations are explicit, version-controlled SQL files that can be audited in code reviews before deployment.
- **Strict Validation**: Hibernate `ddl-auto=validate` verifies entity mappings against Flyway-managed schema without altering database tables dynamically.
- **Environmental Consistency**: Flyway ensures identical database schema evolution across local development, CI pipelines, staging, and production environments.

#### Consequences
- **Positive**: Complete schema versioning history, zero accidental DDL mutations, production-grade deployment safety.
- **Negative / Trade-offs**: Schema changes require writing explicit SQL migration scripts alongside entity modifications.

---

### ADR-014: Spring Environment Profiles and Testcontainers Integration Testing Strategy

**Date**: 2026-08-11  
**Status**: Accepted  
**Phase**: Phase 4B  

#### Context & Problem Statement
Running application integration tests against in-memory H2 databases can mask database-specific SQL dialect bugs, locking behavior differences, and Flyway migration syntax errors that only manifest in production PostgreSQL environments. Conversely, requiring a manually managed external PostgreSQL database for every unit test run causes developer friction and fragile CI builds.

#### Considered Options
1. **Single H2 Environment Configuration**: Simple but lacks production environment parity and risks PostgreSQL dialect/locking incompatibilities.
2. **Environment Profiles (`local`, `test`, `prod`) with Testcontainers PostgreSQL**:
   - `local`: Embedded H2 in PostgreSQL compatibility mode for instant zero-dependency local development (`application-local.yml`).
   - `test`: Real PostgreSQL container booted on-demand via Testcontainers (`@Testcontainers`, `@DynamicPropertySource`) for integration tests (`application-test.yml`).
   - `prod`: External PostgreSQL database configured via environment variables with HikariCP connection pool tuning and graceful shutdown (`application-prod.yml`).

#### Decision Outcome
Chosen Option: **Environment Profiles (`local`, `test`, `prod`) with Testcontainers PostgreSQL**

##### Rationale
- **Production Parity in Tests**: Testcontainers boots a real Dockerized PostgreSQL container during integration test execution, validating Flyway migrations (`V1`..`V4`), dialect behavior, and JPA locking against real PostgreSQL.
- **Instant Local Dev Iteration**: The `local` profile uses H2 in PostgreSQL mode, enabling instant application startup without local Docker overhead.
- **Graceful Execution**: Using `@Testcontainers(disabledWithoutDocker = true)` ensures tests run automatically when Docker is active, while falling back gracefully when running in Docker-less environments.

#### Consequences
- **Positive**: 100% production parity for database integration tests, zero environment drift, clean separation of configuration per profile.
- **Negative / Trade-offs**: Integration test execution requires Docker daemon access when running Testcontainers tests.

---

### ADR-015: SHA-256 Request Payload Hashing & Durable Database-Backed Idempotency Engine

**Date**: 2026-08-15  
**Status**: Accepted  
**Phase**: Phase 6A  

#### Context & Problem Statement
In peer-to-peer payment APIs, network timeouts, client reconnections, and gateway retries frequently cause duplicate HTTP `POST` mutation requests. Without strict idempotency controls, a client retrying a transfer could execute duplicate balance debits and transfers.

#### Considered Options
1. **In-Memory Cache (e.g. Guava/Caffeine)**: Fast, but lost on application restart and cannot be shared across multiple backend server instances.
2. **Distributed Redis Cache**: Low latency, but adds operational infrastructure complexity and risks split-brain/data loss if Redis restarts without AOF persistence.
3. **Durable Database-Backed Registry with SHA-256 Payload Hashing**: Store idempotency records in a dedicated PostgreSQL table (`idempotency_registry`), verified with SHA-256 cryptographic digests, with background scheduled TTL cleanup.

#### Decision Outcome
Chosen Option: **Durable Database-Backed Registry with SHA-256 Payload Hashing**

##### Rationale
- **Zero Double-Spending Guarantee**: Storing records in PostgreSQL ensures ACID durability across node restarts, horizontal scaling, and transactional isolation.
- **Payload Tampering & Reuse Prevention**: Computing a deterministic SHA-256 hash of the raw HTTP request bytes prevents fraudulent client key reuse with modified amounts or recipient UPIs.
- **In-Flight Conflict Detection & Crash Lease Recovery**: Status tracking (`PROCESSING` / `INITIATED`) detects concurrent requests with the same key and rejects them with `409 Conflict`. An in-flight lease expiration window (2 minutes) ensures that orphaned in-flight states from crashed worker nodes automatically unlock for client retries without waiting for the 24-hour TTL purge.
- **Distributed Race Protection**: Simultaneous key inserts on multi-node deployments catching `DataIntegrityViolationException` gracefully map to `409 Conflict`.
- **Cached Replay**: Completed requests (`SUCCESS`) immediately replay the cached HTTP response code and response JSON without re-executing backend balance changes.
- **Performance**: An index on `created_at` (`idx_idemp_created`) guarantees rapid scheduled TTL purge queries without scanning the entire registry table.

#### Consequences
- **Positive**: Absolute protection against duplicate payments, standard financial industry compliance (Stripe/Adyen pattern), zero external infrastructure dependencies, resilience to worker node crashes.
- **Negative / Trade-offs**: Requires database round-trips for mutation requests; requires periodic TTL purge job.

---

### ADR-016: Spring Modulith Event Publication Registry & Transactional Outbox Pattern

**Date**: 2026-08-16  
**Status**: Accepted  
**Phase**: Phase 6B  

#### Context & Problem Statement
When a payment transaction completes, domain events (such as `TransferCompletedEvent`) must be published for asynchronous auditing, notifications, and downstream processing (e.g. Kafka streaming in Phase 9). Directly publishing events over network brokers inside a `@Transactional` business method causes the **dual-write problem**: if the broker call fails after DB commit, or if the DB transaction aborts after message dispatch, the systems fall out of sync.

#### Considered Options
1. **Direct Synchronous Broker Publishing (e.g. Kafka/RabbitMQ in `@Transactional`)**: Vulnerable to the dual-write problem, network latency, and distributed transaction anomalies.
2. **Hand-Rolled Outbox Table with Polling Dispatcher**: Requires custom schema, custom polling workers with advisory locks, dead-letter retry queues, and significant maintenance overhead.
3. **Spring Modulith Event Publication Registry (`spring-modulith-starter-jpa`)**: Framework-managed transactional outbox using `ApplicationEventPublisher`. Events published within `@Transactional` are atomically recorded in an `event_publication` log table in the same database transaction. Async event listeners annotated with `@ApplicationModuleListener` execute outside the publishing transaction and mark the registry entry as completed.

#### Decision Outcome
Chosen Option: **Spring Modulith Event Publication Registry (`spring-modulith-starter-jpa`)**

##### Rationale
- **Zero Dual-Write Problem**: Events are persisted to PostgreSQL within the exact same ACID transaction as the wallet balance mutations and double-entry ledger entries.
- **Transactional Decoupling**: Downstream consumers (`@ApplicationModuleListener`) run in separate asynchronous transactions after commit. Failures in consumers do not fail or roll back the completed financial transfer.
- **Automatic Event Replay & Resilience**: On application restart or retry, uncompleted events in `event_publication` can be re-dispatched (`republish-outstanding-events-on-restart: true`).
- **Architectural Boundary Enforcement**: Spring Modulith provides compile-time / test-time architectural boundary verification (`ApplicationModules.verify()`), ensuring clean modular domain encapsulation without premature microservices decomposition.
- **Zero Custom Boilerplate**: Eliminates hundreds of lines of custom polling daemon and state-tracking code.

#### Consequences
- **Positive**: ACID atomicity for domain events, seamless future externalization to Kafka (Phase 9A), built-in architectural verification.
- **Negative / Trade-offs**: Requires `event_publication` database table managed via Flyway (`V6__create_event_publication_registry.sql`).

---

### ADR-017: Stateless JWT Authentication & Spring Security Architecture

**Date**: 2026-08-17  
**Status**: Accepted  
**Phase**: Phase 7A  

#### Context & Problem Statement
To secure the Payflow API against unauthorized transfers, data breaches, and identity spoofing, all mutation and sensitive query endpoints must enforce strong client authentication. In distributed and horizontally scaled cloud deployments, stateful HTTP sessions (session cookies / server-side session stores) introduce clustering bottlenecks, sticky routing complexity, and cache invalidation challenges.

#### Considered Options
1. **HTTP Basic Authentication**: Simple, but requires transmitting user credentials with every single request, increasing exposure surface.
2. **Stateful Server-Side Sessions (Redis Session Store)**: Secure, but requires distributed cache infrastructure and introduces network hops for every request.
3. **Stateless JSON Web Tokens (JWT) with HMAC-SHA256 (HS256)**: Cryptographically signed, self-contained bearer tokens validated locally in memory by `JwtAuthenticationFilter` without database lookups on every request.

#### Decision Outcome
Chosen Option: **Stateless JSON Web Tokens (JWT) with HMAC-SHA256 (HS256) via Spring Security 6/7**

##### Rationale
- **High Scalability & Zero Session Storage**: Tokens are stateless and self-contained, containing user identity (`upiId`), UUID reference ID (`referenceId`), and role claims (`ROLE_USER`), enabling seamless horizontal auto-scaling without shared session replication.
- **Strong Cryptographic Integrity**: Signed with HMAC-SHA256 (minimum 256-bit secret) using JJWT 0.13.0, preventing token tampering and forgery.
- **Fine-Grained Endpoint Security Filter Chain**: Configured via modern Spring Security `SecurityFilterChain` bean:
  - **Public Endpoints**: `POST /api/v1/auth/login`, `POST /api/v1/users` (registration), Swagger UI (`/swagger-ui/**`), OpenAPI docs (`/v3/api-docs/**`), Actuator health check (`/actuator/health/**`, `/actuator/info`).
  - **Protected Endpoints**: All financial transactions (`/api/v1/transactions/**`), user queries, and ledger audit logs require `Authorization: Bearer <token>`.
- **RFC 7807 Compliance**: Unauthenticated access attempts return uniform `401 Unauthorized` problem details via custom `JwtAuthenticationEntryPoint`.
- **Cross-Origin Resource Sharing (CORS)**: Configured with customizable origin patterns, headers, and HTTP methods for modern web frontends.

#### Consequences
- **Positive**: High throughput stateless authentication, zero database lookup per request for token verification, production-grade Spring Security integration, clean testability via `@WithMockUser` and `authHeaders()` helpers.
- **Negative / Trade-offs**: Tokens cannot be arbitrarily revoked before expiration without maintaining a token blacklist/revocation registry (handled in advanced auth phases). Configurable 1-hour expiration mitigates window of vulnerability.

---

### ADR-018: Principal-Bound Resource Access Control & Sender Verification

**Date**: 2026-08-18  
**Status**: Accepted  
**Phase**: Phase 7B  

#### Context & Problem Statement
In a payment system, authentication (Phase 7A) establishes identity via JWT bearer tokens, but does not inherently restrict which resources the authenticated principal can mutate or inspect. Without explicit fine-grained authorization:
1. An authenticated user `alice@payflow` could submit a transfer request with `senderUpiId: bob@payflow`, fraudulently debiting Bob's balance (Impersonation / Unauthorized Debit).
2. An authenticated user could inspect another user's balance ledger entries (`GET /api/v1/users/{id}/ledger`) or transaction history (`GET /api/v1/transactions/{id}`), violating user data privacy and banking secrecy regulations.

#### Considered Options
1. **Method-Level SpEL Expressions (`@PreAuthorize`) Only**: Declarative Spring Security annotations using SpEL (e.g. `@PreAuthorize("#request.senderUpiId == authentication.name")`). While clean for simple rules, SpEL lacks compile-time type safety, does not easily handle multi-party data visibility (sender OR receiver), and makes unit testing without Spring context heavier.
2. **Filter-Level Role Checks Only**: Restricting endpoints purely by role (`ROLE_USER`, `ROLE_ADMIN`). Fails to enforce resource ownership because all authenticated users share `ROLE_USER`.
3. **Defense-in-Depth Principal-Bound Authorization (Chosen)**:
   - Combine Spring Security `AccessDeniedHandler` (RFC 7807 problem details) for filter-level denials.
   - Enforce programmatic domain-level ownership and participant verification in service and controller layers using `SecurityUtils.getAuthenticatedUpiId()`.
   - Map unauthorized attempts to `ForbiddenOperationException` (`403 Forbidden`).

#### Decision Outcome
Chosen Option: **Defense-in-Depth Principal-Bound Authorization (Option 3)**

##### Rationale
- **Sender Verification on Transfers**: `TransactionService.sendMoney()` asserts `authenticatedUpi.equalsIgnoreCase(request.getSenderUpiId())`. Discrepancies immediately throw `ForbiddenOperationException`, rejecting the transfer before acquiring row locks or debiting balances.
- **Multi-Party Transaction Visibility**: `TransactionService.getTransactionByReferenceId()` ensures that only the **sender** or the **receiver** can view transaction details. Third-party users receive `403 Forbidden`.
- **Double-Entry Balance Ledger Privacy**: `UserService.getUserLedger()` enforces that users can only retrieve ledger entries associated with their own `referenceId` / `upiId`.
- **Profile Ownership**: `UserController.getUserById()` and `getUserByUpiId()` enforce that users can only fetch their own profile details.
- **Uniform Error Standard**: All authorization violations return standardized RFC 7807 `403 Forbidden` (`application/problem+json`) with type `https://api.payflow.com/errors/forbidden-operation` and clear non-leaking error messages.
- **Filter-Level Fallback**: `JwtAccessDeniedHandler` guarantees that any Spring Security framework-level authorization rejection also emits RFC 7807 problem details.

#### Consequences
- **Positive**: Guaranteed protection against unauthorized transfers, data exfiltration, and account impersonation; complete test isolation with zero flaky mock requirements.
- **Negative / Trade-offs**: Requires extracting and verifying `SecurityUtils` principal across private query and mutation service methods.

---

### ADR-019: Declarative HTTP Interface Client (RestClient) & Resilient External Service Integration

**Date**: 2026-08-26  
**Status**: Accepted  
**Phase**: Phase 7C  

#### Context & Problem Statement
During user onboarding (`POST /api/v1/users`), Payflow must verify whether a requested UPI ID is legitimate with an external upstream banking/NPCI verification gateway. In Spring Framework, outbound HTTP communication has historically used `RestTemplate` (now in maintenance/deprecated status) or third-party libraries like Netflix Feign / Spring Cloud OpenFeign (which add heavy runtime overhead and complex dependency trees). Furthermore, third-party network calls are inherently prone to transient connection failures, timeouts, and gateway outages that must not degrade core onboarding availability.

#### Considered Options
1. **Legacy `RestTemplate`**: Synchronous template client. Verbose, lacks fluent builder APIs, and deprecated in modern Spring Framework.
2. **Spring Cloud OpenFeign**: Declarative client annotations, but requires Spring Cloud BOM, Ribbon/LoadBalancer dependencies, and heavy reflection infrastructure.
3. **Declarative HTTP Interface Client backed by `RestClient` (Chosen)**: Native Spring Framework 6.1+ / Spring Boot 4 feature (`@HttpExchange` / `@GetExchange`) with `RestClientAdapter` and `HttpServiceProxyFactory`, combined with Spring Retry (`@Retryable` with exponential backoff, multiplier, and randomized jitter) and `@Recover` graceful degradation.

#### Decision Outcome
Chosen Option: **Declarative HTTP Interface Client via `RestClient` + Spring Retry (Option 3)**

##### Rationale
- **Modern Spring Standard**: `RestClient` provides a fluent, synchronous HTTP client with full access to HTTP headers, timeouts, and status handlers without legacy template boilerplate.
- **Declarative Type Safety**: `UpiValidationClient` defines contract interfaces with `@HttpExchange` and `@GetExchange`, automatically proxied by `HttpServiceProxyFactory` without boilerplate client implementations.
- **Resilience via Exponential Backoff & Jitter**: Transient network glitches and 5xx errors are retried up to 3 times with exponential backoff (`delay = 500ms`, `multiplier = 2.0`, `random = true` jitter) to mitigate thundering-herd retry storms.
- **Non-Blocking Graceful Fallback**: If upstream validation fails persistently or times out, the `@Recover` handler (`UpiValidationService.recoverFromValidationFailure`) logs a warning and proceeds with registration. Third-party vendor outages never take down user registration.
- **Domain Rejection on Explicit Failure**: If the external gateway explicitly reports `valid = false`, registration is blocked with `InvalidUpiException` (`422 Unprocessable Entity`).

#### Consequences
- **Positive**: Zero third-party Feign dependencies, modern compile-time verifiable HTTP interface contracts, automated retry resilience with jitter, and high availability onboarding through graceful fallback.
- **Negative / Trade-offs**: Requires mock server setup (`MockRestServiceServer` / `@MockitoBean`) in test suites when `payflow.upi-validation.enabled=true`.







