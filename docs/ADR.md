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
