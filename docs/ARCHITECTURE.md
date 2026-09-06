# Payflow API — System Architecture & Design Document

> **Document Metadata**
> - **Title**: Payflow API Core System Architecture & Payment Engine Design
> - **Author**: Payflow Engineering (`shashakchandel@gmail.com`)
> - **Status**: Approved / Living Design Document
> - **Created Date**: 2026-08-01
> - **Last Updated**: 2026-09-06
> - **Authoritative Location**: [ARCHITECTURE.md](ARCHITECTURE.md)
> - **Related Documents**: [API Specification](API_SPECIFICATION.md) | [Security Architecture](SECURITY.md) | [Architecture Decisions (ADRs)](adr/README.md) | [Phased Roadmap](ROADMAP.md) | [Engineering Conventions](CONVENTIONS.md)

---

## Executive Summary & Objective

Payflow API is an enterprise-grade peer-to-peer (P2P) payment backend and transaction ledger designed to process high-concurrency financial transfers with zero double-spending, guaranteed idempotency, and full auditability.

The system is built as a **Spring Modulith modular monolith** — enforcing strict package-level module boundaries while maintaining single-database ACID transactions for financial safety. Domain events are published via Spring's `ApplicationEventPublisher` and persisted atomically through Spring Modulith's Event Publication Registry, enabling a seamless evolutionary path from in-process event handling to Apache Kafka event streaming without modifying domain service code.

The core objective of this system design is to solve the fundamental challenges of payment processing — race conditions during concurrent balance mutations, partial failures during dual-writes (database vs event broker), entity enumeration security risks, and non-deterministic floating-point financial arithmetic — while providing an extensible, cloud-native architecture adaptable from single-server deployments (`prod-light`) to distributed Kubernetes clusters (`prod`).

---

## Business Background & Context

In financial payment systems, balance state corruption or double-spending causes direct monetary loss and loss of customer trust. Traditional CRUD architectures fail under concurrent payment spikes (e.g. flash sales or bill splitting) because uncoordinated database reads and writes lead to race conditions where two simultaneous transactions debit the same starting balance twice.

Furthermore, communicating transaction state to downstream services (such as notification push, fraud monitoring, or rewards engines) via direct HTTP/message calls within a database transaction causes the **dual-write problem**: if the DB commit succeeds but the network call fails, or vice versa, the system enters an inconsistent state.

Payflow solves these challenges through:
1. **Deterministic Lock Ordering**: Alphabetical pessimistic row locking on user accounts to prevent deadlocks and race conditions.
2. **Double-Entry Balance Ledger**: Immutable, append-only ledger entries preserving complete audit trails.
3. **Transactional Outbox Pattern**: Writing outbound events to the database in the same ACID transaction as the state change, eliminating network dual-write failures.
4. **Durable Idempotency Engine**: SHA-256 request payload hashing to intercept and safely replay duplicate network submissions.

---

## Goals & Non-Goals

### Goals
- **Zero Double-Spending**: Guarantee absolute atomicity and isolation for balance transfers under concurrent requests.
- **Financial Precision**: Enforce exact base-10 arithmetic (`BigDecimal`, `precision = 19, scale = 4`) with banker's rounding (`HALF_EVEN`).
- **Exactly-Once Mutation Semantics**: Enforce idempotency on payment endpoints via unique `Idempotency-Key` headers and SHA-256 payload verification.
- **Auditability**: Maintain an append-only transaction and balance ledger history where balances can be independently audited and reconciled.
- **Sub-100ms P95 Latency**: Deliver fast transfer execution under peak concurrent load.
- **Non-Enumerable Entities**: Protect internal primary keys (`Long userId`) by exposing immutable UUID reference IDs (`referenceId`) across all external APIs.

### Non-Goals
- **Multi-Currency / Forex Conversion Engine**: Version 1 is scoped strictly to single-currency transactions (INR). Multi-currency conversion is explicitly out of scope for v1.
- **Physical ATM / Card Issuance Protocol**: Card network rails (Visa/Mastercard ISO 8583) are out of scope.
- **Direct Banking Clearing House Clearing**: Core banking settlement protocols (NPCI/ISO 20022) are mocked via clean domain adapter interfaces.

---

## Service Level Objectives (SLOs) & System Constraints

| Metric | Target / Limit | Enforcement Mechanism |
| :--- | :--- | :--- |
| **Availability** | `99.99%` uptime | Kubernetes multi-pod deployment with health probes |
| **P95 Latency (Transfer)** | `< 100ms` | Index-optimized SQL queries, connection pool tuning |
| **P99 Latency (Transfer)** | `< 250ms` | Deterministic lock ordering, minimal transaction holding time |
| **Throughput Baseline** | `500 TPS` / node | HikariCP pool optimization & Virtual Threads (`Project Loom`) |
| **Double-Spend Tolerance** | `0` (Zero tolerance) | Database pessimistic write locks (`SELECT ... FOR UPDATE`) |
| **Data Retention** | `7 Years` (Audit requirement) | Append-only database ledger & historical partition tables |

---

## 1. System Topology & Core Flow

The diagram below maps the target environment topology, showcasing both the synchronous request-response flow and the asynchronous event-driven pipelines.

```mermaid
graph TD
    %% Clients and Gateway
    Client["Client App"] -- "HTTP POST (with Idempotency-Key)" --> Gateway["API Gateway (Spring Security / JWT / v1 Versioning)"]

    subgraph "Kubernetes Pod / Spring Boot Container"
        Gateway --> Controller["UserController / TransactionController"]
        Controller --> Service["TransactionService / UserService / AIService"]
        Service --> Locking["Pessimistic Lock / Redis Distributed Lock"]
        Service --> Idempotency["Idempotency Filter & Registry"]
        Service --> EventPub["ApplicationEventPublisher"]
        EventPub --> ModulithLog["Spring Modulith Event Publication Log"]
        ModulithLog --> EventListener["@ApplicationModuleListener"]
        
        %% Observability
        Tracing["Micrometer Tracing + OTel Bridge"] -.-> TraceOut["W3C traceparent propagation"]
        Logging["MDC Logger (Structured JSON)"] -.-> LogOut["traceId / spanId / requestId"]
    end

    %% Databases & Cache
    Locking -- "SELECT ... FOR UPDATE" --> PostgresDB[("PostgreSQL DB")]
    Idempotency -- "Check / Save State" --> PostgresDB
    ModulithLog -- "Atomic write in same TX" --> PostgresDB
    
    Service -- "Cache-Aside / Token Bucket" --> Redis[("Redis Cache / Rate Limiter / Distributed Lock")]

    %% Kafka bridge (Phase 9A)
    ModulithLog -- "spring-modulith-events-kafka" --> Kafka["Apache Kafka Broker (KRaft Mode)"]

    %% Observers
    Prometheus["Prometheus Server"] -.->|"/actuator/prometheus"| Controller
    Grafana["Grafana Dashboards"] -.-> Prometheus
```

---

## 2. Spring Boot Profile Architecture

The system enforces strict execution profiles to maximize scalability and transition seamlessly from a developer's laptop to production clusters:

* **`local` (Default)**: Optimized for zero-dependency local starts. It boots using an in-memory **H2 database** with basic schema generation and mock environment configurations.
* **`test`**: Active during JUnit verification. It disables local startup profiles and utilizes **Testcontainers** to orchestrate isolated Postgres and Kafka instances per test run.
* **`prod`**: Production-grade profile. Schema updates are strictly applied via **Flyway**. External databases (PostgreSQL), memory stores (Redis), and event streaming instances (Kafka) are resolved via Twelve-Factor environment variables.
* **`prod-light` (AWS Free Tier Optimized)**: A memory-restricted deployment profile designed to run on a single constrained AWS virtual server (1 GiB RAM):
  - **Pluggable Redis**: Caching falls back to local in-memory providers (`Caffeine` or `ConcurrentHashMap`), and rate limiting operates in-memory (using Bucket4j local configurations).
  - **Pluggable Kafka**: Drops the Kafka broker dependency. Transaction events are processed internally via Spring `ApplicationEventPublisher` (in-memory queues) or deferred purely inside the Outbox database logs, bypassing JVM broker overhead.

---

## 3. Concurrency Control & Balance Integrity

To prevent double-spending under high request concurrency (e.g., a user submitting multiple transfers simultaneously), Payflow implements two tiers of locking:

### A. Database-Level Pessimistic Locking (Core Ledger)
For balance updates on a single database instance, the transaction service uses database-level pessimistic write locking:
```java
// In UserRepository.java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT u FROM User u WHERE u.upiId = :upiId")
Optional<User> findByUpiIdWithLock(@Param("upiId") String upiId);
```
*This executes a `SELECT ... FOR UPDATE` query in PostgreSQL, blocking other transactions from modifying these specific rows until the current transaction commits.*
* **Deadlock Avoidance**: Lock acquisition is performed in a deterministic order (e.g., sorting the user UPI IDs alphabetically). This prevents deadlock loops when two users perform mutual transfers at the same time.

### B. Distributed Locking (Cross-Service Coordination)
When transaction logic spans distributed systems (such as reserve balance operations, third-party payment gateways, or rate limiting across multiple application nodes), we introduce a **Redis Distributed Lock (Redlock)** via Redisson:
- **Use Case**: Locking a payment request before database transactions begin, preventing thundering herds and duplicate submission processing at the gateway container boundaries.
- **Failover**: Configured with a short lease time (TTL) to prevent permanent resource locking if a pod node crashes during transaction execution.

### C. Append-Only Double-Entry Balance Ledger
All financial balance operations execute double-entry bookkeeping by persisting two immutable `BalanceLedgerEntry` records (`DEBIT` for sender, `CREDIT` for receiver) within the same `@Transactional` database boundary as the money transfer:
- **Audit Integrity**: Every ledger entry records `amount`, `balanceBefore`, and `balanceAfter`, providing an immutable audit trail for every user balance state transition.
- **Balance Reconciliation**: `users.balance` functions as a high-performance denormalized field. The authoritative source of truth can be validated at any time by executing a reconciliation query over the `balance_ledger` table:
  ```sql
  SELECT COALESCE(SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE -amount END), 0)
  FROM balance_ledger WHERE user_id = :userId;
  ```
- **Immutability**: Once written, ledger rows are never updated or deleted. Reversals or refunds append new `CREDIT`/`DEBIT` ledger rows.

### D. Database Indexing Strategy
To ensure high database read throughput and statement generation speed, the following index constraints are established in Flyway migrations:
- `CREATE UNIQUE INDEX idx_users_upi_id ON users(upi_id);`
- `CREATE UNIQUE INDEX idx_users_reference_id ON users(reference_id);`
- `CREATE INDEX idx_tx_sender_created ON transactions(sender_upi_id, created_at DESC);`
- `CREATE INDEX idx_tx_receiver_created ON transactions(receiver_upi_id, created_at DESC);`
- `CREATE INDEX idx_tx_reference_id ON transactions(reference_id);`
- `CREATE INDEX idx_ledger_user_created ON balance_ledger(user_id, created_at DESC);`

### E. Flyway Versioned Database Migrations
All database DDL changes execute via Flyway versioned SQL scripts located in `src/main/resources/db/migration/`:
- `V1__create_users_table.sql`: Baseline schema for `users` table.
- `V2__create_transactions_table.sql`: Schema for `transactions` table with foreign keys to `users`.
- `V3__create_balance_ledger_table.sql`: Schema for `balance_ledger` double-entry audit table.
- `V4__add_performance_indexes.sql`: Composite and unique indexes for UPI lookups, transaction queries, and ledger audit lookups.

Hibernate is configured to `spring.jpa.hibernate.ddl-auto=validate`, forcing JPA mapping validation against Flyway-managed schema while prohibiting unversioned database mutations in production.

### F. Spring Environment Profiles & Testcontainers Strategy
Configuration is structured cleanly across profile-specific YAML files:

| Profile | Datasource / DB Engine | Flyway | JPA DDL Auto | Primary Purpose |
| :--- | :--- | :--- | :--- | :--- |
| **`local`** | H2 In-Memory (`MODE=PostgreSQL`) | Enabled | `validate` | Instant local dev startup without Docker |
| **`test`** | Testcontainers PostgreSQL (`postgres:16-alpine`) | Enabled | `validate` | 100% production-parity integration testing |
| **`prod`** | External PostgreSQL Cluster | Enabled | `validate` | Production deployment with HikariCP tuning & graceful shutdown |

Integration tests extend `AbstractIntegrationTest`, utilizing `@Testcontainers(disabledWithoutDocker = true)` and `@DynamicPropertySource` to dynamically spin up disposable PostgreSQL containers and bind JDBC credentials, ensuring full schema migration and locking verification against real PostgreSQL.

### G. Comprehensive Multi-Tier Testing Strategy (Pyramid Architecture)

Payflow enforces a multi-tier testing strategy following the standard Test Pyramid:

```
                  / \
                 / IT\        <- Testcontainers PostgreSQL Integration Tests (Phase 4B/5B)
                /-----\
               / Slice \      <- WebMvc (@WebMvcTest) & DataJPA (@DataJpaTest) Slice Tests (Phase 5A)
              /---------\
             / Unit Tests\    <- Mockito Service & MapStruct Mapper Unit Tests (Phase 5A)
            /-------------\
```

1. **Service Unit Tests (Mockito)**: Focus on business domain isolation (`UserServiceTest`, `TransactionServiceTest`). Services are tested with mocked repositories, verifying lock acquisition ordering, balance invariance, domain exception handling, and double-entry ledger creation.
2. **WebMvc Controller Slice Tests (`@WebMvcTest`)**: Focus on HTTP interface contract verification (`UserControllerTest`, `TransactionControllerTest`). Test DTO validation (`422 Unprocessable Entity`), RFC 7807 problem detail error responses, HTTP status codes (`201 Created`, `404 Not Found`), and pagination parameter enforcement.
3. **Data JPA Repository Slice Tests (`@DataJpaTest`)**: Focus on SQL query compilation and repository correctness (`UserRepositoryTest`, `BalanceLedgerRepositoryTest`). Verify custom JPQL/SQL aggregate queries (e.g., balance reconciliation `SUM` queries) and pessimistic lock query execution (`SELECT FOR UPDATE`).
4. **MapStruct Mapper Unit Tests**: Verify zero-loss mapping between JPA entities and public DTO records (`UserMapperTest`, `TransactionMapperTest`, `LedgerMapperTest`).
5. **Concurrency & Integration Tests (Testcontainers PostgreSQL)**: Focus on full-stack integration and high-concurrency race condition testing against real PostgreSQL containers (`TransferLifecycleIT`, `ConcurrentTransferIT`, `MutualTransferDeadlockIT`):
   - **Concurrency Testing (`CountDownLatch`)**: Validates double-spend prevention under simultaneous withdrawal requests. 10 synchronized worker threads attempt to withdraw ₹100 from an account with ₹150 balance at the exact same millisecond. Tests assert that exactly 1 succeeds, 9 fail with `InsufficientBalanceException`, and the final balance is ₹50 (never negative).
   - **Deadlock Avoidance Verification**: Simulates simultaneous mutual cross-transfers ($A \rightarrow B$ and $B \rightarrow A$), proving that deterministic alphabetical lock ordering by UPI ID prevents circular wait deadlocks.
   - **Double-Entry Ledger Reconciliation**: Verifies that aggregate JPQL queries ($\sum\text{CREDIT} - \sum\text{DEBIT}$) across the `balance_ledger` table match `users.balance` at all times.

---

## 4. JPA N+1 Query Resolution

When loading transaction histories (e.g., querying users and their related list of transactions), Hibernate's default lazy loading triggers the **N+1 query problem**:
- 1 query is executed to fetch the page of $N$ users.
- $N$ queries are executed to fetch the transactions for each individual user.

### Resolution Strategy
To maintain a high-performance database connection pool, Payflow resolves this using `@EntityGraph` annotations in Spring Data JPA repositories:
```java
// In TransactionRepository.java
@EntityGraph(attributePaths = {"sender", "receiver"})
Optional<Transaction> findByReferenceId(UUID referenceId);

@EntityGraph(attributePaths = {"sender", "receiver"})
Page<Transaction> findBySenderUpiIdOrReceiverUpiId(String senderUpiId, String receiverUpiId, Pageable pageable);
```
This forces Spring Data JPA to generate a single SQL query with `LEFT OUTER JOIN`s, retrieving the transaction along with its associated `sender` and `receiver` `User` entities in a single database round-trip.

---

## 5. API Versioning Strategy

To support continuous releases and backward compatibility for mobile and third-party integrations, the project enforces **URI-based API versioning**:

- **Format**: `/api/v1/...` (e.g., `POST /api/v1/transactions`).
- **Implementation**: Controllers are explicitly mapped under versioned path prefixes:
  ```java
  @RestController
  @RequestMapping("/api/v1/transactions")
  public class TransactionController { ... }
  ```
- **Deprecation Policy**: Old version routes (e.g., `/api/v1`) remain active until deprecated by major versions (`/api/v2`), routing traffic transparently via the API Gateway.

---

## 6. Durable Idempotency Engine

To guarantee "exactly-once" execution on payment mutations, Payflow enforces a durable, database-backed idempotency filter that requires clients to pass a unique `Idempotency-Key` header with write requests.

### Idempotency Schema
```sql
CREATE TABLE idempotency_registry (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(50) NOT NULL, -- INITIATED, PROCESSING, SUCCESS, FAILED
    response_body TEXT,
    response_code INT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_idemp_created ON idempotency_registry(created_at);
```

### Idempotency Filter Lifecycle Flow

```mermaid
sequenceDiagram
    autonumber
    actor Client as 📱 HTTP Client
    participant Filter as 🛡️ IdempotencyFilter
    participant DB as 🗄️ PostgreSQL (idempotency_registry)
    participant Engine as 🔒 TransactionService

    Client->>Filter: POST /api/v1/transactions (Header: Idempotency-Key)
    alt Missing Idempotency-Key
        Filter-->>Client: 400 Bad Request (RFC 7807: Missing Required Header)
    else Key Present
        Filter->>Filter: Compute SHA-256(requestBody)
        Filter->>DB: SELECT * FROM idempotency_registry WHERE idempotency_key = ?
        alt Key Exists & Different Hash
            Filter-->>Client: 400 Bad Request (RFC 7807: Key Reuse with Different Payload)
        else Key Exists & Status is PROCESSING / INITIATED
            alt Lease Expired (> 2 minutes)
                Filter->>DB: UPDATE idempotency_registry SET updated_at = NOW()
                Filter->>Engine: Allow Retry Execution -> sendMoney()
            else Lease Active (<= 2 minutes)
                Filter-->>Client: 409 Conflict (Request Currently In-Flight)
            end
        else Key Exists & Status is SUCCESS
            Filter-->>Client: Replay Cached HTTP Response (Code & JSON Body)
        else Key Not Found
            alt Concurrent Insert Collision (DataIntegrityViolationException)
                Filter-->>Client: 409 Conflict (Concurrent Request Being Processed)
            else Successful Insert
                Filter->>DB: INSERT INTO idempotency_registry (key, hash, status=PROCESSING)
                Filter->>Engine: doFilterInternal() -> sendMoney()
                Engine-->>Filter: HTTP 201 Created (TransactionResponse)
                Filter->>DB: UPDATE idempotency_registry SET status=SUCCESS, response_code=201, response_body=...
                Filter-->>Client: HTTP 201 Created (Original Response)
            end
        end
    end
```

### In-Flight Lease Recovery & Distributed Race Handling
- **Crashed Worker Node Recovery**: If an application node crashes mid-flight while a transaction is in status `PROCESSING`, the in-flight lease automatically expires after 2 minutes (`IN_FLIGHT_TIMEOUT`), allowing client retries to re-acquire the lock without waiting for the 24-hour TTL purge.
- **Concurrent Insert Collision Safety**: If two concurrent requests with the same key arrive simultaneously and both pass initial existence checks, database unique constraint enforcement triggers a `DataIntegrityViolationException`, which the filter catches and gracefully translates to `409 Conflict`.

### Background TTL Purge Service
A scheduled job (`IdempotencyCleanupService`) executes periodically (`payflow.idempotency.cleanup-cron`) to purge expired records older than the configured TTL (`payflow.idempotency.ttl-hours`, default 24 hours). The `idx_idemp_created` B-Tree index ensures constant-time range scan performance during deletion.

---

## 7. Spring Modulith Event Publication & Transactional Outbox

To achieve reliable event-driven messaging, Payflow uses **Spring Modulith's Event Publication Registry** (`spring-modulith-starter-jpa`) to atomically persist domain events within the same database transaction as the business state change. This eliminates the dual-write problem without requiring custom outbox polling infrastructure.

### Event Publication Schema
```sql
CREATE TABLE IF NOT EXISTS event_publication (
    id UUID NOT NULL,
    listener_id VARCHAR(512) NOT NULL,
    event_type VARCHAR(512) NOT NULL,
    serialized_event TEXT NOT NULL,
    publication_date TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_event_pub_completion ON event_publication(completion_date);
CREATE INDEX IF NOT EXISTS idx_event_pub_date ON event_publication(publication_date);
```

### Transactional Outbox Flow

```mermaid
sequenceDiagram
    autonumber
    actor Client as 📱 HTTP Client
    participant Service as 🔒 TransactionService
    participant Publisher as 📢 ApplicationEventPublisher
    participant DB as 🗄️ PostgreSQL (users / transactions / event_publication)
    participant Listener as ⚡ TransferEventListener (@ApplicationModuleListener)

    Client->>Service: sendMoney(TransferMoneyRequest)
    Note over Service,DB: Transaction Boundary: @Transactional(READ_COMMITTED)
    Service->>DB: UPDATE users SET balance = balance - amount WHERE ... (Sender)
    Service->>DB: UPDATE users SET balance = balance + amount WHERE ... (Receiver)
    Service->>DB: INSERT INTO transactions ...
    Service->>DB: INSERT INTO balance_ledger (DEBIT & CREDIT entries) ...
    Service->>Publisher: publishEvent(TransferCompletedEvent)
    Publisher->>DB: INSERT INTO event_publication (id, listener_id, event_type, serialized_event, publication_date)
    Service-->>Client: 201 Created (TransactionResponse)
    Note over Service,DB: Transaction Commits Atomically in Single DB Unit of Work

    Note over DB,Listener: Post-Commit Asynchronous Invocation (Spring Modulith)
    Publisher->>Listener: onTransferCompleted(TransferCompletedEvent)
    Listener->>Listener: Asynchronous audit / notification logging
    Listener->>DB: UPDATE event_publication SET completion_date = NOW() WHERE id = ?
```

### Module Boundary Verification
Spring Modulith continuously validates domain encapsulation and architectural coupling rules across packages via `ModulithStructureTest`:
```java
ApplicationModules modules = ApplicationModules.of(PayflowApiApplication.class);
modules.verify();
```

### Evolutionary Architecture Path

| Stage | Profile | Event Handling | Kafka Required? |
| :--- | :--- | :--- | :--- |
| **Phase 6B** | `local` / `test` | In-process `@ApplicationModuleListener` | No |
| **Phase 9A** | `prod` | Auto-externalized to Kafka via `spring-modulith-events-kafka` | Yes |
| **Phase 12A** | `prod-light` | In-process (no Kafka, single-server deployment) | No |

This design ensures that domain service code (`TransactionService.sendMoney()`) **never changes** regardless of whether events are consumed in-process or streamed to Kafka. The Spring Modulith framework handles the routing transparently based on active Spring profiles.

---

## 8. Spring Security & Stateless JWT Authentication

Payflow secures all financial and private user endpoints using **Spring Security 6/7** and **Stateless JSON Web Tokens (JWT)**. Authentication is completely decoupled from server session state, allowing frictionless horizontal scaling across distributed cloud nodes.

### Authentication & Token Issuance Flow

1. **User Login (`POST /api/v1/auth/login`)**: The client provides their registered `upiId`. Upon successful user lookup, `JwtTokenProvider` generates a cryptographically signed HMAC-SHA256 token containing user claims (`sub: upiId`, `referenceId`, `roles: [ROLE_USER]`, `iat`, `exp`).
2. **Access Token Lifetime**: Configured to 1 hour (3600s) via `payflow.security.jwt.expiration-ms`.
3. **Public vs Protected Route Matrix**:
   - **Public**: `POST /api/v1/auth/login`, `POST /api/v1/users` (onboarding), `/swagger-ui/**`, `/v3/api-docs/**`, `/actuator/health/**`, `/actuator/info`.
   - **Protected**: `POST /api/v1/transactions`, `GET /api/v1/transactions/**`, `GET /api/v1/users/**` (all require `Authorization: Bearer <token>`).

### Security Filter Chain Sequence

```mermaid
sequenceDiagram
    autonumber
    actor Client as 📱 HTTP Client
    participant Filter as 🛡️ JwtAuthenticationFilter
    participant Provider as 🔑 JwtTokenProvider
    participant Context as 🧠 SecurityContextHolder
    participant Controller as 🎯 Protected Controller
    participant EntryPoint as 🚫 JwtAuthenticationEntryPoint

    Client->>Filter: Request with Authorization: Bearer <token>
    alt Missing Authorization Header on Protected Route
        Filter->>Controller: Chain continues without auth
        Controller-->>EntryPoint: Access Denied / Unauthenticated
        EntryPoint-->>Client: 401 Unauthorized (RFC 7807 ProblemDetail)
    else Invalid or Expired Token
        Filter->>Provider: validateToken(token) -> false
        Filter->>Controller: Chain continues without auth
        Controller-->>EntryPoint: Access Denied
        EntryPoint-->>Client: 401 Unauthorized (RFC 7807 ProblemDetail)
    else Valid JWT Bearer Token
        Filter->>Provider: validateToken(token) -> true
        Filter->>Provider: getUpiIdFromToken(token) -> upiId
        Filter->>Context: setAuthentication(UsernamePasswordAuthenticationToken)
        Filter->>Controller: FilterChain.doFilter(req, res)
        Controller-->>Client: 200 / 201 Response Payload
    end
```

### Principal-Bound Resource Authorization & Sender Verification (Phase 7B)

While JWT authentication verifies the client's cryptographic identity, **Principal-Bound Authorization** enforces resource-level permissions and sender verification across all endpoints:

1. **Strict Sender Verification (`POST /api/v1/transactions`)**:
   - `TransactionService.sendMoney()` extracts the authenticated UPI handle via `SecurityUtils.getAuthenticatedUpiId()`.
   - Asserts that `authenticatedUpi.equalsIgnoreCase(request.getSenderUpiId())`.
   - Any attempt by user $A$ to initiate a transfer debited from user $B$'s account is immediately rejected with `403 Forbidden` (`ForbiddenOperationException`) **before** acquiring database row locks or mutating balances.

2. **Multi-Party Transaction Visibility (`GET /api/v1/transactions/{id}`)**:
   - Only the **sender** or the **receiver** participating in a financial transaction is authorized to retrieve its details.
   - Unrelated third-party users attempting to inspect other transactions receive `403 Forbidden`.

3. **Double-Entry Balance Ledger Privacy (`GET /api/v1/users/{id}/ledger`)**:
   - Users are restricted to retrieving their own double-entry ledger audit entries. Cross-user ledger inspection attempts are rejected with `403 Forbidden`.

4. **Standardized RFC 7807 403 Problem Details**:
   - Filter-level security rejections trigger `JwtAccessDeniedHandler`, emitting `application/problem+json` with `403 Forbidden`.
   - Domain-level authorization violations trigger `GlobalExceptionHandler` mapping `ForbiddenOperationException` to uniform RFC 7807 payloads.

### Declarative HTTP Interface Client & Outbound UPI Validation (Phase 7C)

During user onboarding (`POST /api/v1/users`), Payflow integrates with upstream banking / NPCI verification gateways via **Spring 6.1+ Declarative HTTP Interface Clients** backed by fluent `RestClient`:

1. **Declarative Contract (`@HttpExchange` / `@GetExchange`)**:
   - `UpiValidationClient` defines type-safe contract interfaces with zero boilerplate implementation.
   - Dynamic proxies generated at boot via `HttpServiceProxyFactory` and `RestClientAdapter`.

2. **Outbound Resilience with Exponential Backoff & Jitter**:
   - Outbound HTTP calls use Spring Retry (`@Retryable`) with exponential backoff (`delay = 500ms`, `multiplier = 2.0`, `random = true` jitter) up to 3 attempts to prevent thundering herd storms during downstream gateway blips.

3. **High-Availability Non-Blocking Graceful Fallback**:
   - If the external gateway is persistently unreachable or times out, the `@Recover` handler (`UpiValidationService.recoverFromValidationFailure`) logs a warning and proceeds with registration. External third-party outages never compromise user registration availability.
   - If the external gateway explicitly reports `valid = false`, registration is blocked with `InvalidUpiException` (`422 Unprocessable Entity`).

```mermaid
sequenceDiagram
    autonumber
    actor Client as 📱 Mobile Client
    participant Controller as 🎯 UserController
    participant Service as 💼 UserService
    participant Validator as 🛡️ UpiValidationService
    participant HTTPClient as 🌐 UpiValidationClient (RestClient)
    participant Gateway as 🏦 Upstream Banking Gateway

    Client->>Controller: POST /api/v1/users { upiId: "alice@payflow", ... }
    Controller->>Service: registerUser(request)
    Service->>Validator: validateUpi("alice@payflow")
    
    alt Validation Disabled (Feature Flag)
        Validator-->>Service: Skip check (Instant OK)
    else Validation Enabled
        Validator->>HTTPClient: verify("alice@payflow")
        HTTPClient->>Gateway: GET /api/v1/upi/verify/alice%40payflow
        
        alt Success (valid: true)
            Gateway-->>HTTPClient: 200 OK { "valid": true, "bankName": "HDFC" }
            HTTPClient-->>Validator: UpiVerificationResponse(valid=true)
            Validator-->>Service: Proceed with Registration
        else Explicit Rejection (valid: false)
            Gateway-->>HTTPClient: 200 OK { "valid": false }
            HTTPClient-->>Validator: UpiVerificationResponse(valid=false)
            Validator-->>Controller: throw InvalidUpiException (422 Unprocessable Entity)
            Controller-->>Client: 422 ProblemDetail (invalid-upi-id)
        else Gateway Down / Timeout (Network Failure)
            Gateway--xHTTPClient: 503 / Timeout (Attempt 1)
            Note over Validator: Retry 1: Backoff 500ms + Jitter
            Gateway--xHTTPClient: 503 / Timeout (Attempt 2)
            Note over Validator: Retry 2: Backoff 1000ms + Jitter
            Gateway--xHTTPClient: 503 / Timeout (Attempt 3)
            Validator->>Validator: @Recover handler fallback (Proceed with warning)
            Validator-->>Service: Fallback OK (Non-blocking onboarding)
        end
    end
    
    Service->>Service: Persist User Entity to DB
    Service-->>Controller: User Entity
    Controller-->>Client: 201 Created (UserResponse)
```

---

## 9. Gen-AI Spend Insights & Categorization

To support smart financial features, the project includes an **AI spend assistant** integration using **Spring AI** connected to an LLM provider API:

- **AI Categorization**: An asynchronous listener or dedicated endpoint reads transaction metadata and recent `BalanceLedgerEntry` history (amounts, merchant UPI names, transaction notes, DEBIT/CREDIT classifications) and passes a structured prompt to the LLM to map transactions into structured categories (e.g., `Groceries`, `Utilities`, `Entertainment`, `Dining`).
- **Structured JSON Schema**: Prompts leverage the LLM's structured JSON output mode to force the response directly into a predefined JSON schema mapping, preventing formatting errors.
- **Budgeting Insights**: Generates automated personal budgeting recommendations based on the user's double-entry balance ledger audit history via clean prompt engineering and LLM integrations.

---

## 10. Resilience Policies & Thread Tuning

System stability under load is enforced using **Resilience4j** configurations:

* **Rate Limiting**: Enforced at the controller layer via a Redis-backed Token Bucket algorithm. Limits mutations to prevent denial-of-service attempts.
* **Connection & Read Timeouts**: Explicit timeouts configured on the HTTP clients and database connections to prevent thread pool depletion.
* **Retries & Backoff**: Outbound requests (e.g. to third-party banking processors) are wrapped in Resilience4j Retry policies using **exponential backoff with random jitter** to prevent thundering herd requests on recovering downstream hosts.
* **Virtual Threads Integration (Project Loom)**:
  Java 25 virtual threads are enabled to handle high request-response volumes:
  ```properties
  spring.threads.virtual.enabled=true
  ```
  Since virtual threads do not block OS kernel threads, throughput scales efficiently. However, to prevent database pool exhaustion, HikariCP's maximum pool size must be explicitly configured and aligned with Postgres threshold capabilities (e.g., `maximum-pool-size=20`).

---

## 11. Kubernetes Readiness & Pod Lifecycle

The application complies with cloud-native deployment requirements when running in a Kubernetes cluster:

* **Graceful Shutdown**: Enabled using Spring properties:
  ```properties
  server.shutdown=graceful
  ```
  Upon receiving a `SIGTERM` signal, the container stops routing new requests but allows existing, in-flight transaction threads to complete (up to the configured `terminationGracePeriodSeconds` in the Kubernetes pod spec, recommended at `30s`).
* **Health Probes**: Spring Boot Actuator isolates Kubernetes-native endpoints:
  - **Liveness Probe** (`/actuator/health/liveness`): Confirms the application process is running.
  - **Readiness Probe** (`/actuator/health/readiness`): Verifies database connectivity, cache connections, and messaging broker availability.

---

## 12. Observability Stack (Phase 8A)

Payflow implements the **Three Pillars of Observability** — Metrics, Tracing, and Logging — using industry-standard open-source tooling and Spring Boot 4 / Micrometer Observation architecture:

### A. Distributed Tracing (OpenTelemetry & Micrometer Observation)
- **Micrometer Tracing** with the **OpenTelemetry bridge** (`micrometer-tracing-bridge-otel` and `opentelemetry-exporter-otlp`) automatically generates and propagates W3C-standard `traceparent` headers (`traceId`, `spanId`) across HTTP controllers, database queries, and async thread pools.
- **Method-Level Spans**: Domain service operations like `TransactionService.sendMoney()` are instrumented with `@Observed(name = "payflow.transfers.send", contextualName = "send-money-transfer")` via `ObservedAspect`, creating dedicated trace spans and execution timers automatically.
- **Header Correlation**: The client-provided or auto-generated `X-Request-Id` (via `RequestIdFilter`) coexists with W3C `traceparent` context, returning both headers in HTTP responses.
- Compatible with **Grafana Tempo**, **Jaeger**, or any OTLP-compatible tracing backend.

### B. Business & System Metrics (Prometheus + Grafana)
- **Business Metrics (`MetricsConfig.java`)**:
  - `payflow.transfers.total`: Counter tagged by transfer status (`COMPLETED`, `FAILED`, `INSUFFICIENT_BALANCE`, `FORBIDDEN`).
  - `payflow.transfers.amount`: Distribution summary with SLA percentiles (p50, p95, p99) tracking transfer monetary distribution in INR.
  - `payflow.transfers.duration`: Timer with SLA percentiles (p50, p95, p99) tracking end-to-end transfer execution latency.
- **System & Pool Metrics**: Actuator exposes HikariCP connection pool saturation (`PayflowHikariPool`), JVM memory, garbage collection, and thread states.
- **Prometheus Scraping**: Prometheus scrapes `/actuator/prometheus` without authentication barriers (`SecurityConfig` permits actuator metric endpoints).

### C. Structured Logging & MDC Enrichment (`RequestLoggingFilter.java`)
- **MDC Correlation**: `RequestIdFilter` and `RequestLoggingFilter` populate MDC keys across every request:
  - `requestId`: Unique request identifier (`X-Request-Id`).
  - `traceId` and `spanId`: OpenTelemetry trace context.
  - `http.status`, `http.method`, `http.uri`, `http.latency_ms`: HTTP execution telemetry.
- **Environment Profiles**:
  - `prod` profile: Activates native structured JSON logging (`logging.structured.format.console: ecs`) for ingestion by Elasticsearch, Grafana Loki, or CloudWatch.
  - `local` / `test` profiles: Outputs formatted ANSI colored logs:
    ```text
    2026-08-27 20:30:45.123 [http-nio-8080-exec-1] [req-abc-123] [4bf92f3577b34da6a3ce929d0e0e4736,00f067aa0ba902b7] INFO  c.p.f.RequestLoggingFilter - HTTP POST /api/v1/transactions - 201 (18ms)
    ```

---

## 13. Resilience Architecture & Fault Tolerance (Phase 8B)

Payflow API incorporates **Resilience4j** to safeguard system stability under peak traffic spikes, noisy neighbor conditions, and downstream service degradations.

### A. Resilience Policy Configuration Matrix
| Policy | Component | Target | Key Configuration Parameters | Failure Behavior |
| :--- | :--- | :--- | :--- | :--- |
| **Rate Limiter** | `transferLimiter` | `TransactionService.sendMoney()` | `limitForPeriod: 10`, `limitRefreshPeriod: 1s`, `timeoutDuration: 0s` | Returns HTTP `429 Too Many Requests` with `Retry-After: 1` |
| **Circuit Breaker** | `upiValidation` | `UpiValidationService` | `COUNT_BASED`, sliding window `10`, min calls `5`, failure threshold `50%`, wait in open `5s`, half-open calls `3` | Trips to `OPEN`; returns HTTP `503 Service Unavailable` or executes graceful fallback |
| **Time Limiter** | `upiValidation` | Outbound HTTP calls | `timeoutDuration: 5s`, `cancelRunningFuture: true` | Terminates slow hanging requests, preventing thread starvation |
| **Transaction Timeout** | Database Engine | Balance debit/credit & ledger write | `timeout = 5s` (Spring `@Transactional`) | Aborts deadlock-prone or hung SQL transactions |

### B. Dynamic Per-User Rate Limiting Pattern
Unlike traditional global rate limiting, which allows a single abusive script to exhaust server throughput for all customers, Payflow enforces **per-authenticated-user partition isolation**:

```mermaid
sequenceDiagram
    autonumber
    actor Client as Client / User A
    participant Filter as JwtAuthenticationFilter
    participant Resolver as SecurityContextRateLimiterKeyResolver
    participant Service as UserRateLimiterService
    participant Reg as RateLimiterRegistry
    participant Core as TransactionService

    Client->>Filter: POST /api/v1/transactions (Bearer JWT)
    Filter->>Resolver: Resolve User Principal (alice@payflow)
    Resolver->>Service: Key = "transferLimiter:alice@payflow"
    Service->>Reg: rateLimiter("transferLimiter:alice@payflow", "transferLimiter")
    Note over Service,Reg: Inherits base 10 req/s template & tracks access timestamp
    alt Permitted (<= 10 req/s)
        Service->>Core: Proceed with transfer
        Core-->>Client: 201 Created (TransactionResponse)
    else Exceeded (> 10 req/s)
        Service-->>Client: 429 Too Many Requests (Retry-After: 1)
    end
```

- **Memory Eviction Safeguard**: Dynamically generated rate limiter instances are tracked by access timestamp. `UserRateLimiterService.evictInactiveLimiters()` executes periodically, purging partitions inactive for >15 minutes from the registry to prevent unbounded memory growth.

### C. Downstream Circuit Breaking & Selective Exception Filtering
The external UPI verification gateway is guarded by a Resilience4j Circuit Breaker:
- **Count-Based Sliding Window**: Measures the outcome of the last 10 calls. Once 5 calls are completed, if >=50% fail with network exceptions (`RestClientException`, `IOException`), the circuit trips to `OPEN`.
- **Selective Exception Filtering**: Client data errors (such as `InvalidUpiException` mapped to HTTP 422) are domain validation rejections and explicitly excluded via `ignoreExceptions`, ensuring bad user inputs do not falsely trip downstream infrastructure breakers.
- **Fail-Fast & Recovery**: In `OPEN` state, downstream calls fail fast without network traversal. After 5 seconds, the circuit transitions to `HALF_OPEN`, testing 3 probe requests to automatically heal back to `CLOSED`.

### D. Resilience Metrics & Actuator Integration
- **Prometheus Gauges & Counters**:
  - `resilience4j.circuitbreaker.state`: Current state (`closed`, `open`, `half_open`).
  - `resilience4j.circuitbreaker.calls`: Total calls tagged by `kind` (`successful`, `failed`, `ignored`).
  - `resilience4j.ratelimiter.available_permissions`: Current remaining quota per partition.
  - `resilience4j.ratelimiter.waiting_threads`: Threads blocked waiting for permits.
- **Health Indicators**: Exposed under `/actuator/health` with `circuitBreakers` and `rateLimiters` component statuses.

---

## 14. Distributed Caching Architecture & Cache-Aside Pattern (Phase 8C)

To achieve sub-10ms response latencies on repetitive user profile lookups and balance ledger inspection while shielding PostgreSQL from read connection exhaustion, Payflow API implements a **Profile-Conditional Cache-Aside Architecture** using Spring Cache, Redis (Lettuce), and Caffeine.

### A. Cache-Aside Workflow & Sequence Diagram
In the Cache-Aside pattern, the application service inspects the cache before accessing the underlying database. On write operations, the cache entries are evicted rather than updated inline, eliminating race conditions between concurrent database writes and cache updates.

```mermaid
sequenceDiagram
    autonumber
    actor Client as API Client / Controller
    participant Service as UserService / TxService
    participant Cache as CacheManager (Redis / Caffeine)
    participant DB as PostgreSQL Database

    alt Read Query (e.g. getUserByReferenceId)
        Client->>Service: Read User Profile / Ledger
        Service->>Cache: GET key (e.g. "users::refId")
        alt Cache Hit
            Cache-->>Service: Return cached JSON / entity
            Service-->>Client: Return UserResponse / LedgerResponse
        else Cache Miss
            Cache-->>Service: null
            Service->>DB: SELECT query
            DB-->>Service: Entity record
            Service->>Cache: PUT key with configured TTL
            Service-->>Client: Return UserResponse / LedgerResponse
        end
    else Write Mutation (e.g. sendMoney or registerUser)
        Client->>Service: Transfer Funds / Register User
        Service->>DB: Execute ACID transaction & commit
        DB-->>Service: Transaction committed
        Service->>Cache: EVICT affected caches (users, user_ledgers)
        Service-->>Client: Return Success Response
    end
```

### B. Profile-Conditional Cache Strategy
The cache infrastructure adapts transparently across environments via `CacheConfig.java`:

| Environment / Profile | Cache Provider | Implementation Class | Characteristics & Configuration |
| :--- | :--- | :--- | :--- |
| **`prod`** | **Redis (Lettuce)** | `RedisCacheManager` | Centralized distributed cache across multi-pod deployments. Configured via `LettuceConnectionFactory` with standalone host/port. Serialization uses `RedisSerializer.string()` for keys and `RedisSerializer.json()` for values. |
| **`!prod` (`local`, `test`, `prod-light`)** | **Caffeine** | `CaffeineCacheManager` | High-performance in-memory cache requiring zero external network dependencies or Docker containers. Spec: `maximumSize=1000,expireAfterWrite=600s`. |

### C. Cache Names, Key Schemes & TTL Matrix
Cache policies are externalized in `application.yml` and tuned based on data volatility and freshness requirements:

| Cache Name | Cached Methods | Key Scheme | TTL | Eviction Triggers |
| :--- | :--- | :--- | :--- | :--- |
| `users` | `getUserById()`, `getUserByReferenceId()`, `findByUpiId()`, `getUserByUpiId()` | `#id`, `#referenceId`, `#upiId` (single method arg) | **10 minutes** (`600s`) | Purged on `UserService.registerUser()` and `TransactionService.sendMoney()` |
| `user_ledgers` | `getUserLedger()` | `#userReferenceId + '_' + #pageable.pageNumber` | **1 minute** (`60s`) | Purged on `TransactionService.sendMoney()` |

### D. Serialization & Entity Hardening
1. **Modern JSON Serialization (`RedisSerializer.json()`)**:
   Spring Data Redis 3.x / Spring Framework 7 deprecates `GenericJackson2JsonRedisSerializer`. Payflow leverages `RedisSerializer.json()` for non-intrusive, polymorphic JSON value encoding. This avoids native Java binary serialization vulnerabilities while ensuring human-readable inspectability in Redis CLI (`redis-cli`).
2. **Null-Safety Handling**:
   Read operations use `@Cacheable(..., unless = "#result == null")`. When a service method returns `Optional<User>`, Spring's caching aspect unwraps the `Optional` before SpEL condition evaluation. The `#result == null` guard ensures empty optionals are not cached, preventing false positive hits for non-existent users.
3. **Circular Reference & Proxy Isolation**:
   Domain entities are hardened for JSON serialization:
   - `User` and `BalanceLedgerEntry` implement `java.io.Serializable` (`serialVersionUID = 1L`).
   - `BalanceLedgerEntry` is decorated with `@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})` to prevent Jackson serialization failures on uninitialized Hibernate bytecode proxies.
   - Lazy JPA relationships (`user`, `transaction`) in `BalanceLedgerEntry` are annotated with `@JsonIgnore` to eliminate circular reference graphs during JSON marshaling.

---

## 15. Testing Strategy (Rigor, Concurrency & Unit Verification)

To ensure maximum code coverage and high system reliability, the project defines a two-tier testing strategy consisting of isolated unit tests and full-stack integration tests.

### A. Isolated Unit Testing Strategy
Unit tests focus on isolating individual components and verifying business logic without booting the database or messaging middleware:

1. **Controller Layer (MockMVC)**:
   - Evaluates HTTP serialization, URL routing, request DTO validation constraints (e.g. invalid UPI patterns, blank fields), and custom error mapping to RFC 7807 payloads.
   - Tested using Spring's `@WebMvcTest` paired with `@MockitoBean` (standard in Spring Boot 4.1.x) to stub the service layers, ensuring lightning-fast execution.
2. **Service Layer (Mockito)**:
   - Validates business rules, balance invariant checking, and custom exceptions throwing (e.g., `UserNotFoundException` or `InsufficientBalanceException`).
   - Uses Mockito annotations (`@ExtendWith(MockitoExtension.class)`) to isolate business service operations from Spring lifecycle overhead.
3. **Repository Layer (DataJpaTest)**:
   - Confirms that custom derived queries or complex JPQL Fetch Joins compile and execute successfully.
   - Executed via `@DataJpaTest` running against an embedded H2 database instance.
4. **Mocking External Services**:
   - Outbound REST endpoints (UPI validation via HTTP Interface Client), the AI model assistant API, and Kafka brokers are mocked or stubbed during unit verification using WireMock and MockRestServiceServer to prevent flaky test execution and external network dependence.

### B. Advanced Integration Testing (Rigor & Concurrency Verification)
Integration tests verify the full lifecycle of a transaction across actual container dependencies using **Testcontainers** to orchestrate PostgreSQL and Kafka instances during Maven build phases:

1. **Concurrent Race Condition Testing**:
   - To verify pessimistic row lock deadlocks and double-spend safety under high traffic contention:
     - Initialize an `ExecutorService` with a thread pool (size: 10) and a `CountDownLatch` set to 10.
     - Fire 10 concurrent threads at the same millisecond to withdraw $100 from a sender account holding $150.
     - **Assertion**: Only 1 transaction commits successfully, 9 threads fail with validation or locking errors, and the final account balance is exactly $50.
2. **Idempotency Key Lock Contention Testing**:
   - To verify that gateway-level locks block dual-processing on duplicate submissions:
     - Spin up two concurrent threads executing a transaction using the same `Idempotency-Key` and request payload.
     - **Assertion**: Thread 1 succeeds; Thread 2 is immediately rejected with a `409 Conflict` (custom `IdempotentRequestProcessingException`) without starting any database transaction, proving the gateway Redis lock isolated the duplicate execution path.
3. **Spring Modulith Event Publication Testing**:
   - Confirms domain events published via `ApplicationEventPublisher` are atomically persisted to the event publication log and consumed by `@ApplicationModuleListener` handlers.
   - **Assertion**: Execute a transfer, verify `TransferCompletedEvent` appears in the event publication table within the same database transaction. Verify Kafka externalization (prod profile) via Testcontainers Kafka consumer.

---

## 16. Entity Model & Rich Domain Architecture

To establish robust domain boundaries and prevent corrupt data state, the entity layer adheres to Rich Domain Model principles and precise database constraints:

### A. Financial Precision (`BigDecimal`)
All monetary columns (`balance`, `amount`) are represented using `BigDecimal` mapped to database column definition `@Column(precision = 19, scale = 4, nullable = false)`. Floating-point binary arithmetic primitives (`double`, `float`) are prohibited to eliminate representation error accumulation.

### B. Rich Domain Invariants
Entities encapsulate their own business invariants and state transitions:
- **`User.debit(BigDecimal amount)`**: Enforces positive debit amounts and verifies balance adequacy (`balance >= amount`). Throws `IllegalStateException` or domain exceptions if invariants fail.
- **`User.credit(BigDecimal amount)`**: Enforces positive credit amounts and updates account balance atomically in-memory.

### C. Auditability & Optimistic Locking
- **Audit Timestamps**: `@CreationTimestamp Instant createdAt` and `@UpdateTimestamp Instant updatedAt` automatically track record creation and updates.
- **Optimistic Locking**: `@Version Long version` enables Hibernate to prevent lost updates during concurrent non-locking state updates.

### D. Relational Foreign Key Integrity
The `Transaction` entity maintains explicit JPA `@ManyToOne(fetch = FetchType.LAZY)` foreign key relationships to `User` for `sender` and `receiver`, while retaining denormalized `senderUpiId` and `receiverUpiId` fields for index-optimized queries.

---

## 17. External Service Integration (RestClient & HTTP Interface Client)

Payflow validates UPI IDs against an external validation service during user registration using modern Spring outbound HTTP communication patterns:

### A. RestClient (Spring Framework 7)
`RestClient` is the modern synchronous HTTP client that replaces the deprecated `RestTemplate`. It provides a fluent, immutable API with built-in error handling and serialization support.

### B. HTTP Interface Client
Payflow defines outbound API contracts as declarative Java interfaces using `@GetExchange` / `@PostExchange` annotations:

```java
public interface UpiValidationClient {
    @GetExchange("/verify/{upiId}")
    UpiVerificationResponse verify(@PathVariable String upiId);
}
```

Spring generates the implementation proxy backed by `RestClient` at runtime — conceptually similar to OpenFeign but fully Spring-native with zero external dependencies.

### C. Resilience (Framework 7 Native @Retryable)
Outbound HTTP calls are wrapped with Framework 7's native `@Retryable` annotation (now part of `spring-core`) providing exponential backoff with jitter:

```
Attempt 1 → timeout → wait 500ms
Attempt 2 → timeout → wait ~1000ms (with jitter)
Attempt 3 → success or fallback
```

Graceful fallback: if the UPI validation service is unavailable, registration proceeds with a warning log — external service failures never block core user onboarding.

### D. HTTP Client Comparison Matrix
| Client | Era | Model | Use Case |
| :--- | :--- | :--- | :--- |
| `RestTemplate` | Spring 3 (deprecated in FW7) | Imperative, verbose | Legacy codebases |
| `RestClient` | Spring 6.1+ | Fluent, synchronous | Modern blocking apps |
| `WebClient` | Spring 5+ | Reactive, non-blocking | Reactive pipelines / streaming |
| HTTP Interface Client | Spring 6+ (enhanced in FW7) | Declarative proxy | Clean API contracts, replaces Feign |
| OpenFeign | Spring Cloud | Declarative proxy (external lib) | Legacy Cloud-native apps |

---

## 18. Security, Privacy & Threat Modeling

Payment backends operate under strict security and regulatory requirements. The system architecture addresses key threat vectors:

### A. Threat Matrix & Mitigations
| Threat Vector | Severity | Architectural Mitigation |
| :--- | :--- | :--- |
| **Resource Enumeration** | High | Non-enumerable `UUID referenceId` used in all external URLs and API response payloads instead of auto-incrementing database IDs (`userId`). |
| **Double-Spending / Race Condition** | Catastrophic | Pessimistic DB write locks (`SELECT ... FOR UPDATE`) with deterministic lock acquisition order (alphabetical sorting by UPI ID). |
| **Replay Attacks** | High | Mandatory `Idempotency-Key` headers on mutation endpoints backed by SHA-256 request body hashing. |
| **Unauthorized Transfers** | Critical | JWT bearer token verification matching authenticated subject against sender identity (Phase 7B). |
| **Log Data Leakage (PII/Financial)** | Medium | MDC logging filter strips sensitive fields (card numbers, PINs) and loggers only log `X-Request-Id` correlation context. |

### B. Privacy & Compliance Controls
- **TLS 1.3 Transport Security**: Mandatory for all external gateway ingress traffic.
- **Sanitized Error Responses**: Production error handlers return standard RFC 7807 `ProblemDetail` bodies without exposing raw SQL errors, stack traces, or internal server directory paths.

---

## 19. Alternatives Considered & Trade-off Analysis

Documenting rejected alternatives and evaluating the penalty ("cost of getting it wrong") is critical to preventing architectural regressions.

| Decision Area | Chosen Solution | Alternative Considered | Cost of Getting It Wrong (Penalty) | Why Alternative Was Rejected |
| :--- | :--- | :--- | :--- | :--- |
| **Financial Precision** | `BigDecimal` | `double` / `float` | **Catastrophic**: Cumulative floating-point rounding errors lead to un-reconcilable ledger balances. | Binary floating-point cannot represent base-10 decimals exactly (`0.1 + 0.2 != 0.3`). |
| **Concurrency Control** | Deterministic Pessimistic Locking | Optimistic Locking (`@Version`) | **High**: Under flash-sale high concurrency, optimistic lock collisions cause high transaction abort/retry rates and poor user experience. | Optimistic locking is ideal for read-heavy workloads; money transfers are write-contended on popular seller accounts. |
| **Event Publishing** | Spring Modulith Event Publication Registry | Direct Publish (Service calls Kafka inside `@Transactional`) | **High**: Network failure to Kafka causes rollback of DB transaction or lost events (Dual-write problem). | Direct publishing breaks transactional atomicity between DB state and messaging topics. |
| **Event Publishing** | Spring Modulith Event Publication Registry | Hand-rolled outbox table + `SKIP LOCKED` polling | **Medium**: Custom outbox code requires boilerplate polling dispatcher, manual completion tracking, and retry logic. | Spring Modulith provides framework-managed event persistence, completion callbacks, and transparent Kafka bridging. |
| **API Identification** | Opaque `UUID referenceId` | Exposed `Long userId` | **Medium**: Attackers enumerate total user count and scrape user data via `/api/v1/users/1`, `/2`, `/3`. | Auto-increment IDs leak business metrics and expose predictable resource endpoints. |
| **DTO Serialization** | MapStruct (Compile-time) | Reflection (e.g. `BeanUtils.copyProperties`) | **Medium**: Runtime reflection errors, zero compile-time safety, poor performance under high TPS. | MapStruct generates clean, zero-reflection Java bytecode during Maven build with strict type checking. |

---

## 20. Open & Resolved Design Issues

### Resolved Design Decisions
- **`RES-001`: MapStruct for DTO Mapping** — Resolved in Phase 2C. Replaced custom manual factories with type-safe MapStruct mappers.
- **`RES-002`: RFC 7807 Error Standard** — Resolved in Phase 2D. Standardized all exception responses on Spring `ProblemDetail`.
- **`RES-003`: User Reference IDs** — Resolved in Phase 2E. Added `UUID referenceId` to `User` entity to insulate API boundaries from auto-increment IDs.
- **`RES-004`: Modular Monolith over Microservices** — Resolved in architecture review. Spring Modulith enforces module boundaries while preserving single-database ACID safety for financial transactions.
- **`RES-005`: OpenTelemetry over Vendor-Specific Tracing** — Resolved in architecture review. Micrometer Tracing + OTel bridge provides portable, W3C-standard distributed tracing.
- **`RES-006`: RestClient + HTTP Interface Client over RestTemplate** — Resolved in architecture review. `RestTemplate` is deprecated in Framework 7; `RestClient` + declarative `@GetExchange`/`@PostExchange` interfaces are the modern standard.
- **`RES-007`: Framework 7 @Retryable + Resilience4j Complementary Use** — Resolved in architecture review. Use spring-core native `@Retryable` for basic outbound HTTP retry; use Resilience4j for advanced patterns (per-user rate limiting, circuit breakers) not yet in spring-core.
- **`RES-008`: Profile-Conditional Cache-Aside (Redis in Prod, Caffeine in Dev/Test)** — Resolved in Phase 8C. Decoupled local development from external Redis while ensuring multi-pod cluster state consistency with JSON value serialization.

### Open Questions & Future Evaluation
- **`OPEN-001`: Transfer Amount Cap Configuration** — Default cap set to ₹1,00,000 (`100,000.00`). Evaluating whether per-user dynamic velocity caps should be stored in Redis.

