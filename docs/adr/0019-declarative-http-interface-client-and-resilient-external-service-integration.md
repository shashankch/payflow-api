# ADR-019: Declarative HTTP Interface Client (RestClient) & Resilient External Service Integration

* **Date**: 2026-08-26
* **Status**: Accepted
* **Phase**: Phase 7C

## Context & Problem Statement
During user onboarding (`POST /api/v1/users`), Payflow must verify whether a requested UPI ID is legitimate with an external upstream banking/NPCI verification gateway. In Spring Framework, outbound HTTP communication has historically used `RestTemplate` (now in maintenance/deprecated status) or third-party libraries like Netflix Feign / Spring Cloud OpenFeign (which add heavy runtime overhead and complex dependency trees). Furthermore, third-party network calls are inherently prone to transient connection failures, timeouts, and gateway outages that must not degrade core onboarding availability.

## Considered Options
1. **Legacy `RestTemplate`**: Synchronous template client. Verbose, lacks fluent builder APIs, and deprecated in modern Spring Framework.
2. **Spring Cloud OpenFeign**: Declarative client annotations, but requires Spring Cloud BOM, Ribbon/LoadBalancer dependencies, and heavy reflection infrastructure.
3. **Declarative HTTP Interface Client backed by `RestClient` (Chosen)**: Native Spring Framework 6.1+ / Spring Boot 4 feature (`@HttpExchange` / `@GetExchange`) with `RestClientAdapter` and `HttpServiceProxyFactory`, combined with Spring Retry (`@Retryable` with exponential backoff, multiplier, and randomized jitter) and `@Recover` graceful degradation.

## Decision Outcome
Chosen Option: **Declarative HTTP Interface Client via `RestClient` + Spring Retry (Option 3)**

### Rationale
* **Modern Spring Standard**: `RestClient` provides a fluent, synchronous HTTP client with full access to HTTP headers, timeouts, and status handlers without legacy template boilerplate.
* **Declarative Type Safety**: `UpiValidationClient` defines contract interfaces with `@HttpExchange` and `@GetExchange`, automatically proxied by `HttpServiceProxyFactory` without boilerplate client implementations.
* **Resilience via Exponential Backoff & Jitter**: Transient network glitches and 5xx errors are retried up to 3 times with exponential backoff (`delay = 500ms`, `multiplier = 2.0`, `random = true` jitter) to mitigate thundering-herd retry storms.
* **Non-Blocking Graceful Fallback**: If upstream validation fails persistently or times out, the `@Recover` handler (`UpiValidationService.recoverFromValidationFailure`) logs a warning and proceeds with registration. Third-party vendor outages never take down user registration.
* **Domain Rejection on Explicit Failure**: If the external gateway explicitly reports `valid = false`, registration is blocked with `InvalidUpiException` (`422 Unprocessable Entity`).

## Consequences
* **Positive**: Zero third-party Feign dependencies, modern compile-time verifiable HTTP interface contracts, automated retry resilience with jitter, and high availability onboarding through graceful fallback.
* **Negative / Trade-offs**: Requires mock server setup (`MockRestServiceServer` / `@MockitoBean`) in test suites when `payflow.upi-validation.enabled=true`.
