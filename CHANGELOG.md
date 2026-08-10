# Changelog

All notable changes to the Payflow API project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

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
