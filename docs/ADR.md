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

>>>>>>> Stashed changes
