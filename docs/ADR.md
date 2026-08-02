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
