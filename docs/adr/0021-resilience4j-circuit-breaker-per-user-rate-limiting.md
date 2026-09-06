# ADR-021: Resilience4j Circuit Breaking, Per-User Rate Limiting & Fault-Tolerance Policies

* **Date**: 2026-09-05
* **Status**: Accepted
* **Phase**: Phase 8B

## Context & Problem Statement
High-concurrency financial payment engines face two critical operational risks:
1. **Noisy Neighbor & DoS Starvation**: A single abusive or malfunctioning client firing thousands of requests per second can exhaust database connection pools, thread pools, and CPU cycles, denying service to legitimate participants.
2. **Cascading Downstream Failures**: When third-party external dependencies (such as external banking networks, UPI verification gateways, or upcoming AI insights services) suffer degraded performance, high latencies, or connection timeouts, callers block waiting for responses. This quickly exhausts application worker threads, causing catastrophic cascading collapse across the entire system.

Standard single-tenant rate limiting and naive retry loops fail in financial systems because:
- Global rate limiting allows one aggressive user to consume the entire API quota, blocking everyone else.
- Indiscriminate retries on failing downstream services produce a "retry storm" (thundering herd), driving struggling external backends into total outage.

## Considered Options
1. **Bucket4j with Redis**: Token bucket rate limiting backed by Redis. Provides distributed token coordination, but adds an external network dependency (Redis) for local/monolithic workloads and does not provide circuit breaking or time limiting out-of-the-box.
2. **Guava RateLimiter / Semaphore Controls**: Primitive in-memory token/permit limiters without Spring Boot integration, metrics export, circuit breaker state machines, or declarative annotations.
3. **Resilience4j Ecosystem with Dynamic Per-User Key Resolution (Chosen)**:
   - `resilience4j-spring-boot3` providing lightweight, modular fault tolerance (CircuitBreaker, RateLimiter, TimeLimiter) with native Spring AOP and Actuator auto-configuration.
   - Dynamic per-user rate limiting via `RateLimiterRegistry` and Spring Security `SecurityContextHolder` key resolution (10 requests/second per authenticated user partition, with bounded memory eviction).
   - Count-based sliding window CircuitBreaker (`COUNT_BASED`, window size 10, minimum 5 calls, 50% failure rate threshold, 5s recovery wait duration) on external calls (`UpiValidationService`) with selective failure classification (excluding 4xx business validation exceptions).
   - RFC 6585 and RFC 7807 compliance returning HTTP `429 Too Many Requests` with `Retry-After: 1` headers and HTTP `503 Service Unavailable`.
   - `resilience4j-micrometer` exporting state gauges and call timers to Prometheus (`/actuator/prometheus`) and health indicators to `/actuator/health`.

## Decision Outcome
Chosen Option: **Resilience4j Ecosystem with Dynamic Per-User Key Resolution (Option 3)**

### Rationale
* **Per-User Fairness & Isolation**: Keying rate limiters by authenticated user principal (`alice@payflow`, `bob@payflow`) guarantees that user A exceeding 10 req/s receives HTTP 429 without impacting user B's throughput.
* **Fail-Fast Downstream Protection**: When the external UPI verification gateway fails consecutively, the circuit trips to `OPEN`, immediately shielding downstream services and returning graceful fallbacks without wasting thread pool capacity.
* **Selective Exception Filtering**: Client errors (such as `InvalidUpiException` 422 Unprocessable Entity) are classified as business rejections rather than service outages, preventing invalid user input from tripping the infrastructure circuit breaker.
* **Bounded In-Memory Footprint**: `UserRateLimiterService` implements periodic eviction for partitions inactive for >15 minutes, preventing unbounded memory growth in high-traffic multi-tenant deployments.
* **Unified Observability**: Resilience state transitions and available permits are exported to Prometheus and Actuator health endpoints alongside HikariCP and payment transaction metrics.

## Consequences
* **Positive**: Complete protection against abusive client flooding, deterministic thread containment on downstream outages, RFC 6585 compliance, and full Prometheus visibility.
* **Negative / Trade-offs**: In-memory rate limiting is local to each JVM instance; multi-node clusters in Phase 8C/8D will complement this with distributed Redis-backed rate limiting and caching.
