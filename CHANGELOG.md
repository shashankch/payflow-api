# Changelog

All notable changes to the Payflow API project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Added - Phase 7C (RestClient, Declarative HTTP Interface Client & External UPI Validation)
- Added `spring-retry` dependency to `pom.xml` for declarative retry management.
- Created `UpiVerificationResponse.java` record representing third-party UPI verification response payloads.
- Created `UpiValidationClient.java` declarative HTTP Interface Client interface with `@HttpExchange` and `@GetExchange` annotations.
- Created `RestClientConfig.java` configuring `RestClient`, connection/read timeouts, `@EnableRetry`, and `HttpServiceProxyFactory` proxy generation.
- Created `InvalidUpiException.java` domain exception and mapped to RFC 7807 `422 Unprocessable Entity` in `GlobalExceptionHandler.java`.
- Created `UpiValidationService.java` implementing `@Retryable` outbound calls with exponential backoff and randomized jitter (`delay = 500ms`, `multiplier = 2.0`, `random = true`) and `@Recover` graceful degradation fallback on downstream service outages.
- Integrated UPI validation into `UserService.registerUser()` during client onboarding.
- Created `UpiValidationClientTest.java` verifying HTTP Interface Client serialization and deserialization via `MockRestServiceServer`.
- Created `UpiValidationServiceTest.java` unit tests verifying validation pass, invalid UPI rejection, retry, and recover fallback.
- Created `UpiValidationIT.java` Testcontainers PostgreSQL integration test verifying end-to-end user onboarding with upstream validation and failure handling.
- Added ADR-019 (*Declarative HTTP Interface Client (RestClient) & Resilient External Service Integration*) to `docs/ADR.md`.

### Added - Phase 7B (Authorization & Sender Verification)
- Created `ForbiddenOperationException.java` domain exception extending `PayflowException`.
- Created `SecurityUtils.java` static helper to retrieve the authenticated principal's UPI handle from `SecurityContextHolder`.
- Created `JwtAccessDeniedHandler.java` implementing Spring Security's `AccessDeniedHandler`, emitting standardized RFC 7807 `403 Forbidden` (`application/problem+json`) problem details on access denial.
- Enhanced `SecurityConfig.java` to register `JwtAccessDeniedHandler` in the security filter chain.
- Enhanced `GlobalExceptionHandler.java` with `@ExceptionHandler` for `ForbiddenOperationException` and `AccessDeniedException` mapping to `403 Forbidden`.
- Enforced strict sender authorization in `TransactionService.sendMoney()`: rejecting transfer requests when the authenticated subject does not match `senderUpiId` with `403 Forbidden` before acquiring database locks or mutating balances.
- Enforced multi-party transaction visibility in `TransactionService.getTransactionByReferenceId()` and `getUserTransactions()`: ensuring only transaction participants (sender/receiver) or the account owner can view records.
- Enforced balance ledger privacy in `UserService.getUserLedger()`: preventing users from inspecting other users' double-entry balance ledger audit entries.
- Enforced profile ownership in `UserController.getUserById()` and `getUserByUpiId()`.
- Created full-stack integration test `AuthorizationIT.java` against Testcontainers PostgreSQL verifying all 403 Forbidden boundaries and authorized user flows.
- Added ADR-018 (*Principal-Bound Resource Access Control & Sender Verification*) to `docs/ADR.md`.

### Added - Phase 7A (Spring Security & Stateless JWT Authentication)
- Added `spring-boot-starter-security`, `spring-security-test`, and JJWT 0.13.0 (`jjwt-api`, `jjwt-impl`, `jjwt-jackson`) dependencies to `pom.xml`.
- Created `JwtTokenProvider.java` for HMAC-SHA256 (HS256) token generation, signature validation, expiration checking, and user claims extraction (`upiId`, `referenceId`, `roles`).
- Created `JwtAuthenticationFilter.java` (`OncePerRequestFilter`) to parse Bearer tokens from `Authorization` header and populate `SecurityContextHolder`.
- Created `JwtAuthenticationEntryPoint.java` rendering standardized RFC 7807 `401 Unauthorized` problem details (`application/problem+json`) on unauthenticated requests to protected endpoints.
- Created `SecurityConfig.java` configuring stateless `SecurityFilterChain`, CORS policy with configurable origins/methods/headers, CSRF disabled, public route permit rules (`POST /api/v1/auth/login`, `POST /api/v1/users`, `/swagger-ui/**`, `/v3/api-docs/**`, `/actuator/health/**`), and protected route authentication requirements (`anyRequest().authenticated()`).
- Created `AuthController.java` with `POST /api/v1/auth/login` endpoint issuing JWT tokens with 1-hour expiration and user reference metadata.
- Created DTOs `LoginRequest.java` and `AuthResponse.java`.
- Created unit tests `JwtTokenProviderTest.java` and `AuthControllerTest.java`.
- Created full-stack integration test `AuthenticationIT.java` against Testcontainers PostgreSQL verifying 401 unauthorized rejection, login token acquisition, and authenticated transaction execution.
- Added ADR-017 (*Stateless JWT Authentication & Spring Security Architecture*) to `docs/ADR.md`.

---

## [0.6.0] - 2026-08-16

### Added - Phase 6B (Spring Modulith Events & Transactional Outbox)
- Integrated Spring Modulith 2.0 (`spring-modulith-starter-jpa`, `spring-modulith-starter-test`, and `spring-modulith-bom`).
- Created Flyway migration `V6__create_event_publication_registry.sql` creating `event_publication` table with completion and publication date indexes.
- Created `TransferCompletedEvent` domain event record containing transaction reference UUID, sender/receiver UPI IDs, amount, status, and post-transfer balances.
- Updated `TransactionService.java` to publish `TransferCompletedEvent` via `ApplicationEventPublisher` within the `@Transactional` boundary, achieving transactional outbox persistence to PostgreSQL with zero dual-write vulnerability.
- Created `TransferEventListener.java` annotated with `@ApplicationModuleListener` for asynchronous post-commit event consumption.
- Created `ModulithStructureTest.java` verifying architectural boundaries and package encapsulation with `ApplicationModules.of(PayflowApiApplication.class).verify()`.
- Created `OutboxIT.java` full-stack integration test verifying atomic event publication to `event_publication` table against Testcontainers PostgreSQL.
- Hardened `IdempotencyFilter.java` with in-flight lease timeout (2 min) for automatic recovery from crashed worker nodes, and catch for `DataIntegrityViolationException` to gracefully handle concurrent insert collisions as `409 Conflict`.
- Enhanced `GlobalExceptionHandler.java` with structured error logging (`LOG.error`) for unhandled server exceptions and data integrity violations.
- Standardized deterministic alphabetical lock acquisition in `TransactionService.java` with `String.CASE_INSENSITIVE_ORDER`.
- Added ADR-016 (*Spring Modulith Event Publication Registry & Transactional Outbox Pattern*) to `docs/ADR.md`.

### Added - Phase 6A (Durable Idempotency Engine)
- Created `IdempotencyFilter.java` (`OncePerRequestFilter`) with `CachedBodyHttpServletRequest` wrapper to intercept `POST /api/v1/transactions`, enforcing mandatory `Idempotency-Key` header with SHA-256 request payload hashing.
- Added database persistence via `idempotency_registry` table and `IdempotencyRecord` entity with lifecycle states (`PROCESSING`, `SUCCESS`, `FAILED`).
- Created Flyway migration `V5__create_idempotency_registry.sql` with performance index `idx_idemp_created`.
- Implemented cached HTTP response replay: repeated identical requests immediately return cached 201 Created response without triggering backend transfer logic or double-debiting user balances.
- Implemented validation and conflict handling: rejecting missing `Idempotency-Key` headers or key reuse with mismatched payloads with `400 Bad Request`, and concurrent in-flight requests with `409 Conflict` (RFC 7807 problem details).
- Created `IdempotencyCleanupService.java` with `@Scheduled` purge job for removing expired idempotency records past configured TTL (`payflow.idempotency.ttl-hours`, default 24h).
- Added unit test suite `IdempotencyFilterTest.java` and integration test suite `IdempotencyIT.java` against Testcontainers PostgreSQL.
- Added ADR-015 (*SHA-256 Request Payload Hashing & Durable Database-Backed Idempotency Engine*) to `docs/ADR.md`.

---

## [0.5.0] - 2026-08-15

### Added - Phase 5B (Integration & Concurrency Test Suites)
- Created `TransferLifecycleIT.java` full-stack integration test verifying end-to-end user registration, money transfers, updated balances, and double-entry ledger audit verification against Testcontainers PostgreSQL.
- Created `ConcurrentTransferIT.java` high-concurrency race condition test with 10 synchronized threads (`CountDownLatch`), asserting that simultaneous withdrawals from an account with insufficient balance for all result in exactly 1 success, 9 failures, zero double-spending, and balance invariance (balance never goes negative).
- Created `MutualTransferDeadlockIT.java` verifying deadlock avoidance under concurrent mutual cross-transfers ($A \rightarrow B$ and $B \rightarrow A$) via deterministic alphabetical lock ordering.
- Added `spring-boot-resttestclient` dependency to `pom.xml` for Spring Boot 4.x `TestRestTemplate` autoconfiguration.
- Added structured SLF4J logging across `TransactionService` and `UserService` for enhanced transaction lifecycle observability.

### Added - Phase 5A (Comprehensive Unit & Slice Test Suite)
- Created `UserServiceTest.java` verifying user registration, duplicate UPI prevention, UUID reference lookups, and pagination.
- Created `UserRepositoryTest.java` data JPA slice test verifying custom query compilation, UUID lookups, and pessimistic locking (`SELECT FOR UPDATE`).
- Created `BalanceLedgerRepositoryTest.java` data JPA slice test verifying aggregate balance reconciliation JPQL queries (`SUM(CREDIT) - SUM(DEBIT)`) and paginated audit retrieval.
- Enhanced `UserControllerTest.java` and `TransactionControllerTest.java` WebMvc slice tests verifying HTTP contracts, input validation (`422 Unprocessable Entity`), RFC 7807 problem details, and paginated ledger endpoints (`GET /api/v1/users/{id}/ledger`).
- Added modular test starters `spring-boot-starter-data-jpa-test` and `spring-boot-starter-flyway` to `pom.xml`.

---

## [0.4.0] - 2026-08-11

### Added - Phase 4B (Spring Profiles & Testcontainers Integration)
- Profile-specific YAML configuration structure (`application.yml`, `application-local.yml`, `application-test.yml`, `application-prod.yml`).
- Integrated Testcontainers PostgreSQL (`org.testcontainers:postgresql`) and `spring-boot-testcontainers` BOM.
- Abstract base class `AbstractIntegrationTest.java` with `@Testcontainers(disabledWithoutDocker = true)` and `@DynamicPropertySource` for 100% production-parity integration testing.
- Created `PostgreSQLIntegrationTest.java` verifying real PostgreSQL container startup, Flyway schema migration execution, and Hibernate `ddl-auto=validate` verification.
- Added `ADR-014` (Spring Environment Profiles and Testcontainers Integration Testing Strategy) to `docs/ADR.md`.

### Added - Phase 4A (Flyway Migrations & PostgreSQL Integration)
- Version-controlled Flyway DDL migration scripts (`V1__create_users_table.sql`, `V2__create_transactions_table.sql`, `V3__create_balance_ledger_table.sql`, `V4__add_performance_indexes.sql`).
- Performance indexes added to database schema for UPI lookups (`idx_users_upi_id`), UUID reference lookups (`idx_users_reference_id`, `idx_tx_reference_id`), transaction history statements (`idx_tx_sender_created`, `idx_tx_receiver_created`), and balance ledger audits (`idx_ledger_user_created`).
- Added PostgreSQL driver (`postgresql`), `flyway-core`, and `flyway-database-postgresql` dependencies.
- Converted monolithic `application.properties` configuration to structured `application.yml` setting `spring.jpa.hibernate.ddl-auto=validate`.
- Added `ADR-013` (Flyway Database Migrations over DDL Auto-Generation) to `docs/ADR.md`.

---

## [0.3.0] - 2026-08-09

### Added - Phase 3C (Balance Ledger & Reconciliation)
- Double-entry balance ledger (`balance_ledger` table & `BalanceLedgerEntry` entity) writing atomic `DEBIT` (sender) and `CREDIT` (receiver) records on money transfers.
- Balance audit tracking capturing `amount`, `balanceBefore`, and `balanceAfter` state transitions for complete financial auditability.
- Balance reconciliation aggregate SQL query `calculateReconciledBalanceByUserId()` in `BalanceLedgerRepository` allowing reconstruction of authoritative balance state from ledger rows.
- `GET /api/v1/users/{id}/ledger` endpoint returning paginated balance ledger history for a user by UUID reference ID.
- Added `ADR-012` (Double-Entry Balance Ledger as Immutable Audit Trail) to `docs/ADR.md`.

### Added - Phase 3B (Pessimistic Locking & Deadlock Avoidance)
- Pessimistic write locking (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) on `UserRepository.findByUpiIdWithLock()` generating `SELECT ... FOR UPDATE` SQL statements to prevent race conditions during high-concurrency balance mutations.
- Deterministic alphabetical lock acquisition ordering by UPI ID in `TransactionService.sendMoney()` to prevent database deadlock cycles during concurrent reciprocal transfers.
- JPA N+1 query optimization via `@EntityGraph(attributePaths = {"sender", "receiver"})` on `TransactionRepository` query methods.
- Added `ADR-010` (Pessimistic Locking for High-Concurrency Balance Operations) and `ADR-011` (Deterministic Lock Ordering for Deadlock Prevention) to `docs/ADR.md`.

### Added - Phase 3A (Money Transfer Implementation)
- Money transfer orchestration service (`TransactionService.sendMoney()`) executed under `@Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class, timeout = 5)`.
- Domain level validation for self-transfer rejection (`SelfTransferException`), user verification (`UserNotFoundException`), and balance adequacy (`InsufficientBalanceException`).
- `GET /api/v1/transactions/{id}` endpoint to fetch transaction details by UUID reference ID.
- `GET /api/v1/transactions/user/{upiId}` endpoint to retrieve paginated transfer history for a given UPI ID.
- Comprehensive unit tests (`TransactionServiceTest`) and mock controller tests (`TransactionControllerTest`) covering transfer orchestration and transaction queries.

---

## [0.2.0] - 2026-08-06

### Added - Phase 2E (Model Refinements & Service Hardening)
- Added non-enumerable `UUID referenceId` to `User` entity to insulate REST APIs from auto-increment primary keys (`userId`).
- Added `@Transactional(readOnly = true)` annotations across all `UserService` read methods for Hibernate dirty-checking optimization.
- Consolidated balance check validation directly inside `User.debit()` throwing `InsufficientBalanceException`.
- Enforced transfer upper bound cap (`@DecimalMax("1000000.00")`) on `TransferMoneyRequest`.
- Added MDC `%X{requestId}` tracking pattern to application console logger.
- Removed obsolete static `fromEntity()` factories from response DTO records.
- Added `ADR-007` (UUID Reference IDs over Auto-Increment Primary Keys in APIs) to `docs/ADR.md`.

### Added - Phase 2D (Error Handling & RFC 7807 Exception Framework)
- Implemented custom domain exception hierarchy (`PayflowException`, `UserNotFoundException`, `TransactionNotFoundException`, `InsufficientBalanceException`, `DuplicateUpiIdException`, `SelfTransferException`).
- Implemented global exception handling via `@RestControllerAdvice` (`GlobalExceptionHandler`) producing standardized RFC 7807 `ProblemDetail` responses.
- Implemented `RequestIdFilter` (`OncePerRequestFilter`) for `X-Request-Id` MDC logging and response header correlation tracking.
- Updated domain services (`UserService`, `TransactionService`) and controllers to throw domain exceptions for clean, centralized handling.
- Added unit test suites for `GlobalExceptionHandlerTest` and `RequestIdFilterTest`.
- Added `ADR-006` (RFC 7807 ProblemDetail & Centralized Exception Handling) to `docs/ADR.md`.

### Added - Phase 2C (Mapper Layer & API Documentation)
- Integrated MapStruct `1.6.3` compile-time mappers (`UserMapper`, `TransactionMapper`) for type-safe DTO <-> Entity conversions.
- Integrated Springdoc OpenAPI `3.0.3` (`springdoc-openapi-starter-webmvc-ui`) for live interactive Swagger UI (`/swagger-ui.html`) and OpenAPI JSON specs (`/v3/api-docs`).
- Added OpenAPI configuration bean (`OpenApiConfig`) and controller OpenAPI annotations (`@Tag`, `@Operation`, `@ApiResponse`).
- Added MapStruct mapper unit test suite (`UserMapperTest`, `TransactionMapperTest`).
- Added `ADR-005` (MapStruct for compile-time type-safe DTO mapping) to `docs/ADR.md`.

### Added - Phase 2B (DTO Layer, Input Validation & API Versioning)
- Versioned REST controllers under `/api/v1/users` and `/api/v1/transactions`.
- Added `spring-boot-starter-validation` dependency for Jakarta Validation (`@Valid`, `@NotBlank`, `@Pattern`, `@DecimalMin`, `@Size`, `@Min`, `@Max`).
- Implemented request DTOs: `CreateUserRequest` and `TransferMoneyRequest` with strict validation rules.
- Implemented response DTO records: `UserResponse`, `TransactionResponse`, and generic `PagedResponse<T>` pagination wrapper.
- Added controller web slice tests (`UserControllerTest`, `TransactionControllerTest`) verifying HTTP status codes and input validation enforcement.
- Added `ADR-004` (URI-based API Versioning and DTO Isolation Layer) to `docs/ADR.md`.

### Added - Phase 2A (Entity Model Hardening & Rich Domain)
- Replaced `Double` primitives with `BigDecimal` (`precision = 19, scale = 4`) across `User` and `Transaction` entities.
- Implemented Rich Domain methods (`User.debit()`, `User.credit()`) encapsulating balance invariants and state validation.
- Added audit timestamps (`createdAt`, `updatedAt`) and optimistic locking (`@Version version`) support to `User`.
- Added `TransactionStatus` (`INITIATED`, `COMPLETED`, `FAILED`, `REFUNDED`) and `TransactionType` (`TRANSFER`, `REFUND`) enums.
- Added JPA `@ManyToOne` foreign key relationships between `Transaction` and `User` entities with denormalized UPI strings.
- Added UUID `referenceId` auto-generation (`@PrePersist`) on `Transaction`.
- Refactored all services (`UserService`, `TransactionService`) and controllers (`UserController`, `TransactionController`) to use constructor injection.
- Unit test suite for `User` domain logic and `Transaction` reference ID auto-generation (`UserTest`, `TransactionTest`).
- Architectural Decision Records: `ADR-001` (BigDecimal), `ADR-002` (Constructor injection), `ADR-003` (Rich Domain Model).

---

## [0.1.0] - 2026-07-27

### Added
- Baseline Phase 0/1 implementation.
- Basic User (`/users`) and Transaction (`/transactions`) REST endpoints.
- Spring Data JPA entities (`User`, `Transaction`) and repositories.
- In-memory H2 database persistence for local development builds.
- Initial Spring Boot 4.1.0 project configuration with Java 25.
- System Architecture documentation (`docs/ARCHITECTURE.md`), API Specification (`docs/API_SPECIFICATION.md`), and Phased Roadmap (`docs/ROADMAP.md`).
- Project scaffolding: MIT `LICENSE`, `CHANGELOG.md`, `.editorconfig`.
- Architectural Decision Records log (`docs/ADR.md`) and engineering standards guide (`docs/CONVENTIONS.md`).
- Spotless code formatting plugin (`com.diffplug.spotless:spotless-maven-plugin`) integrated into Maven build.
- Checkstyle static analysis (`checkstyle.xml`) plugin (`maven-checkstyle-plugin`) enforcing coding rules on `validate` phase.
- GitHub Actions CI pipeline (`.github/workflows/ci.yml`) for automated build, linting, formatting, and test execution on Java 25.
- Repository contribution guidelines and branch protection rules (`CONTRIBUTING.md`).
- GitHub status badges in `README.md`.

### Fixed
- Dockerfile JDK version mismatch: `eclipse-temurin:21-jre` → `eclipse-temurin:25-jre` to align with project's `java.version`.
- Broken GraalVM native profile in `pom.xml`: removed invalid dependency declaration with undefined property reference.
- Removed `System.out.println` debug statement from `UserController`.
- README updated to accurately reflect current project status.
