# ADR-004: URI-Based API Versioning and DTO Isolation Layer

* **Date**: 2026-08-01
* **Status**: Accepted
* **Phase**: Phase 2B

## Context & Problem Statement
Exposing JPA entities directly through REST controllers risks over-posting, entity leak, tight coupling of API contracts to database schemas, and backwards-incompatible API breaks whenever internal domain models evolve. Additionally, unversioned API endpoints make contract evolution hazardous for clients.

## Considered Options
1. **Direct JPA Entity Exposure (Unversioned)**: Simple initial implementation, but leaks database structure, enables mass assignment vulnerabilities, and lacks contract stability.
2. **Header or Parameter Versioning**: Flexible versioning via HTTP headers (`Accept: application/vnd.payflow.v1+json`), but harder to cache via HTTP reverse proxies and less clear in logs.
3. **Explicit URI Path Versioning (`/api/v1/`) with DTO Isolation**: Explicit `/api/v1/` prefix with dedicated request objects (Java classes with Jakarta Validation) and response records (`UserResponse`, `TransactionResponse`, `PagedResponse`).

## Decision Outcome
Chosen Option: **URI Path Versioning (`/api/v1/`) with DTO Isolation**

### Rationale
* **Contract Stability**: Isolates REST API contracts from internal JPA entities using Java records for responses and validated DTO classes for requests.
* **Security & Validation**: Enforces exact field constraints (`@NotBlank`, `@Pattern`, `@DecimalMin`, `@Max`) at the API entry point via Jakarta Validation (`@Valid`), preventing malformed requests from reaching business logic.
* **Observability & Caching**: URI versioning (`/api/v1/`) provides clean HTTP proxy caching and transparent log routing across API gateway boundaries.

## Consequences
* **Positive**: Strict decoupling between database tables and API responses; robust validation; backward compatibility pathway (`/api/v2/` in future phases).
* **Negative / Trade-offs**: Mapping code required between DTOs and entities (`UserResponse.fromEntity()`).
* **Risks & Mitigations**: Maintain mapping logic in static factory methods on response records to keep controllers clean and performant.
