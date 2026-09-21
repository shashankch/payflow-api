# ADR-025: Production Hardening, Targeted Cache Invalidation, and Enterprise Security Controls

* **Date**: 2026-09-20
* **Status**: Accepted
* **Phase**: Phase 9B

## Context & Problem Statement

Following an end-to-end Principal Engineer architectural and code audit of the Payflow API, multiple production-readiness gaps, latent security exposures, and scalability bottlenecks were identified across security, exception handling, data caching, transactional outbox lifecycle, and testing:

1. **Production JWT Secret Fallback Vulnerability (`SEC-01`)**:
   In `JwtTokenProvider`, if the `PAYFLOW_SECURITY_JWT_SECRET` environment variable was omitted in a production environment, the application silently fell back to a hardcoded, publicly visible test secret, allowing attackers to forge arbitrary user and admin tokens.
2. **Broken Object Level Authorization & Balance Enumeration (`SEC-02`)**:
   Endpoints `GET /api/v1/users` (bulk user directory) and `GET /api/v1/users/balance/{amount}` (balance threshold querying) lacked administrative role constraints, permitting any authenticated standard user to harvest customer profiles, phone numbers, and account balances.
3. **Transport & Frame Security Exposures (`SEC-03`, `SEC-04`)**:
   Spring Security configuration allowed credential inclusion (`allowCredentials = true`) on wildcard origins (`allowedOrigins = *`), violating the Fetch/CORS specification and opening credential exposure risks. Additionally, `frameOptions().disable()` disabled clickjacking defenses globally.
4. **Spring 6.1+ / Spring 7 Parameter Validation Handling (`ARCH-02`)**:
   Method parameter validation violations (`@Min`, `@Max`, `@PathVariable`, `@RequestParam`) in Spring Framework 6.1+ / 7 throw `HandlerMethodValidationException` rather than `MethodArgumentNotValidException`, which resulted in generic unhandled 500 errors instead of standardized RFC 9457 `ProblemDetail` responses.
5. **Unbounded Idempotency Key Injection (`ARCH-03`)**:
   The `Idempotency-Key` header was accepted without length or character constraints, allowing arbitrarily large strings to cause database column overflows (`VARCHAR(255)`) or resource exhaustion in distributed locks.
6. **Unbounded Growth in Transactional Outbox Registry (`ARCH-04`)**:
   Spring Modulith persists completed event publications to the `event_publication` table. Without an automated maintenance job, this table grows indefinitely under high transfer volumes, degrading query performance and exhausting database storage.
7. **Cache Stampede via Blanket Eviction (`PERF-01`)**:
   On every transfer, `@CacheEvict(value = {"users", "user_ledgers"}, allEntries = true)` wiped all user profiles and ledger histories across the entire application, causing heavy thundering-herd database queries under concurrent traffic.
8. **Integration Test Execution Blindspot (`TEST-01`, `TEST-02`)**:
   `pom.xml` lacked `maven-failsafe-plugin`, causing all containerized integration tests (`*IT.java` covering concurrency, deadlock avoidance, and Kafka outbox delivery) to be bypassed during `mvn test` and CI builds. Furthermore, repository slice tests (`@DataJpaTest`) were absent for `TransactionRepository` and `IdempotencyRepository`.

## Considered Options

### For Cache Eviction:
- **Option A (Global Invalidation)**: Continue using `@CacheEvict(allEntries = true)`. Simple, but causes massive cache stampedes and database churn in production.
- **Option B (Targeted Invalidation — Chosen)**: Programmatically evict only keys belonging to the transaction participants (`sender` and `receiver` by `id`, `upiId`, and `referenceId`, plus sender/receiver ledger caches) via `CacheManager`. Unrelated user cache entries remain untouched.

### For Outbox Cleanup:
- **Option A (Custom SQL Cron)**: Run custom native SQL `DELETE FROM event_publication WHERE completion_date < ...`. Bypasses Spring Modulith abstraction and couples cleanup to vendor-specific table schemas.
- **Option B (Spring Modulith CompletedEventPublications — Chosen)**: Inject `CompletedEventPublications` from `spring-modulith-events-api` and invoke `deletePublicationsOlderThan(Duration.ofDays(7))` via a scheduled `@Transactional` job at 02:00 AM UTC.

### For Production JWT Protection:
- **Option A (Rely on Deployment Checklist)**: Hope operators set `PAYFLOW_SECURITY_JWT_SECRET`. Fragile and prone to human error.
- **Option B (Fail-Fast Environment Invariant Check — Chosen)**: Inject `Environment` into `JwtTokenProvider` constructor. When `environment.matchesProfiles("prod")`, verify secret is non-null, distinct from `DEFAULT_SECRET`, and $\ge$ 256 bits (32 chars); throw `IllegalStateException` on bootstrap otherwise.

## Decision Outcome

We selected the production-grade options across all audit dimensions:

1. **Security**:
   - `JwtTokenProvider` validates secret length and prevents default secret usage in `prod`.
   - `UserController` enforces `SecurityUtils.hasRole("ADMIN")` on `getUsers` and `getUsersWithBalanceAbove`, throwing `ForbiddenOperationException` (HTTP 403).
   - `SecurityConfig` enforces `frameOptions().sameOrigin()` and disallows credentials when `allowedOrigins` contains `*`.
2. **Error Handling & Input Validation**:
   - `GlobalExceptionHandler` handles `HandlerMethodValidationException`, `ConstraintViolationException`, and `MethodArgumentTypeMismatchException`, returning RFC 9457 `ProblemDetail` with 422 and 400 statuses.
   - `IdempotencyFilter` validates `Idempotency-Key` length ($\le 255$) and format (`^[A-Za-z0-9_.:-]+$`), rejecting invalid keys with 400 Bad Request.
3. **Outbox Maintenance**:
   - `OutboxCleanupService` runs daily at 02:00 AM UTC using Spring Modulith's `CompletedEventPublications.deletePublicationsOlderThan(Duration.ofDays(7))`.
4. **Performance**:
   - `TransactionService.evictTargetedCaches()` selectively invalidates only sender and receiver cache keys (`users` and `user_ledgers`), preventing cache stampedes for unrelated active users.
5. **Testing & Build Lifecycle**:
   - `maven-failsafe-plugin` 3.5.6 bound to `integration-test` and `verify` phases with `<include>**/*IT.java</include>`.
   - Added `@DataJpaTest` slices `TransactionRepositoryTest` and `IdempotencyRepositoryTest`.
6. **Persistence Schema Validation Alignment**:
   - Extended `V6__create_event_publication_registry.sql` with `event_publication_archive` table and indexes to satisfy Spring Modulith 2.0's `ArchivedJpaEventPublication` entity during Hibernate `ddl-auto: validate`.
   - Aligned `User.phoneNumber` length constraint (`length = 10`) and `Transaction` foreign key nullability (`nullable = false`) to match Flyway migration DDL definitions.

## Consequences

### Positive
- **Zero Authentication Bypass**: Production deployment cannot start with insecure default JWT secrets.
- **Zero BOLA Vulnerability**: Directory scraping and balance enumeration are strictly blocked for non-admin tokens.
- **Stable Cache Hit Ratio**: Transfers no longer wipe cached sessions of unrelated users.
- **Bounded Database Growth**: Outbox records are automatically pruned after the 7-day retention window.
- **Guaranteed Integration Verification**: All Testcontainers tests run consistently in standard Maven verify lifecycles.

### Negative / Trade-offs
- Targeted cache eviction requires explicit coordination of multiple key formats (`upi:`, `ref:`, `id:`) rather than a single blanket eviction annotation.
- Administrative operations require minting admin tokens with `ROLE_ADMIN` authority in test environments (`@WithMockUser(roles = "ADMIN")`).
