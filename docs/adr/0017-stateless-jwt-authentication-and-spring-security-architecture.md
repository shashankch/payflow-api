# ADR-017: Stateless JWT Authentication & Spring Security Architecture

* **Date**: 2026-08-17
* **Status**: Accepted
* **Phase**: Phase 7A

## Context & Problem Statement
To secure the Payflow API against unauthorized transfers, data breaches, and identity spoofing, all mutation and sensitive query endpoints must enforce strong client authentication. In distributed and horizontally scaled cloud deployments, stateful HTTP sessions (session cookies / server-side session stores) introduce clustering bottlenecks, sticky routing complexity, and cache invalidation challenges.

## Considered Options
1. **HTTP Basic Authentication**: Simple, but requires transmitting user credentials with every single request, increasing exposure surface.
2. **Stateful Server-Side Sessions (Redis Session Store)**: Secure, but requires distributed cache infrastructure and introduces network hops for every request.
3. **Stateless JSON Web Tokens (JWT) with HMAC-SHA256 (HS256)**: Cryptographically signed, self-contained bearer tokens validated locally in memory by `JwtAuthenticationFilter` without database lookups on every request.

## Decision Outcome
Chosen Option: **Stateless JSON Web Tokens (JWT) with HMAC-SHA256 (HS256) via Spring Security 6/7**

### Rationale
* **High Scalability & Zero Session Storage**: Tokens are stateless and self-contained, containing user identity (`upiId`), UUID reference ID (`referenceId`), and role claims (`ROLE_USER`), enabling seamless horizontal auto-scaling without shared session replication.
* **Strong Cryptographic Integrity**: Signed with HMAC-SHA256 (minimum 256-bit secret) using JJWT 0.13.0, preventing token tampering and forgery.
* **Fine-Grained Endpoint Security Filter Chain**: Configured via modern Spring Security `SecurityFilterChain` bean:
  * **Public Endpoints**: `POST /api/v1/auth/login`, `POST /api/v1/users` (registration), Swagger UI (`/swagger-ui/**`), OpenAPI docs (`/v3/api-docs/**`), Actuator health check (`/actuator/health/**`, `/actuator/info`, `/actuator/prometheus`).
  * **Protected Endpoints**: All financial transactions (`/api/v1/transactions/**`), user queries, and ledger audit logs require `Authorization: Bearer <token>`.
* **RFC 7807 Compliance**: Unauthenticated access attempts return uniform `401 Unauthorized` problem details via custom `JwtAuthenticationEntryPoint`.
* **Cross-Origin Resource Sharing (CORS)**: Configured with customizable origin patterns, headers, and HTTP methods for modern web frontends.

## Consequences
* **Positive**: High throughput stateless authentication, zero database lookup per request for token verification, production-grade Spring Security integration, clean testability via `@WithMockUser` and `authHeaders()` helpers.
* **Negative / Trade-offs**: Tokens cannot be arbitrarily revoked before expiration without maintaining a token blacklist/revocation registry (handled in advanced auth phases). Configurable 1-hour expiration mitigates window of vulnerability.
