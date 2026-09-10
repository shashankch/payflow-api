# Architecture Decision Records (ADRs)

This directory contains the formal log of all architectural decisions made for the **Payflow API** payment engine, adhering to standard Architecture Decision Record (ADR) conventions used in production engineering organizations.

Each record documents the business and technical context, options evaluated, rationale for the chosen decision, and resulting trade-offs.

---

## 📑 ADR Index

| ADR # | Title | Date | Status | Phase |
| :--- | :--- | :--- | :--- | :--- |
| [0001](0001-use-bigdecimal-for-monetary-values.md) | [Use BigDecimal for All Monetary Values](0001-use-bigdecimal-for-monetary-values.md) | 2026-08-01 | **Accepted** | Phase 2A |
| [0002](0002-constructor-injection-over-field-injection.md) | [Constructor Injection Over Field Injection](0002-constructor-injection-over-field-injection.md) | 2026-08-01 | **Accepted** | Phase 2A |
| [0003](0003-rich-domain-model-over-anemic-domain-model.md) | [Rich Domain Model Over Anemic Domain Model](0003-rich-domain-model-over-anemic-domain-model.md) | 2026-08-01 | **Accepted** | Phase 2A |
| [0004](0004-uri-based-api-versioning-and-dto-isolation-layer.md) | [URI-Based API Versioning and DTO Isolation Layer](0004-uri-based-api-versioning-and-dto-isolation-layer.md) | 2026-08-01 | **Accepted** | Phase 2B |
| [0005](0005-mapstruct-for-compile-time-type-safe-dto-mapping.md) | [MapStruct for Compile-Time Type-Safe DTO Mapping](0005-mapstruct-for-compile-time-type-safe-dto-mapping.md) | 2026-08-02 | **Accepted** | Phase 2C |
| [0006](0006-rfc-7807-problemdetail-and-centralized-exception-handling.md) | [RFC 7807 ProblemDetail & Centralized Exception Handling](0006-rfc-7807-problemdetail-and-centralized-exception-handling.md) | 2026-08-03 | **Accepted** | Phase 2D |
| [0007](0007-uuid-reference-ids-over-auto-increment-primary-keys.md) | [UUID Reference IDs Over Auto-Increment Primary Keys in APIs](0007-uuid-reference-ids-over-auto-increment-primary-keys.md) | 2026-08-05 | **Accepted** | Phase 2E |
| [0008](0008-spring-modulith-modular-monolith-over-distributed-microservices.md) | [Spring Modulith Modular Monolith Over Distributed Microservices](0008-spring-modulith-modular-monolith-over-distributed-microservices.md) | 2026-08-07 | **Accepted** | Phase 6B |
| [0009](0009-opentelemetry-distributed-tracing-via-micrometer-bridge.md) | [OpenTelemetry Distributed Tracing via Micrometer Bridge](0009-opentelemetry-distributed-tracing-via-micrometer-bridge.md) | 2026-08-07 | **Accepted** | Phase 8A |
| [0010](0010-pessimistic-locking-for-high-concurrency-balance-operations.md) | [Pessimistic Locking for High-Concurrency Balance Operations](0010-pessimistic-locking-for-high-concurrency-balance-operations.md) | 2026-08-07 | **Accepted** | Phase 3B |
| [0011](0011-deterministic-lock-ordering-for-deadlock-prevention.md) | [Deterministic Lock Ordering for Deadlock Prevention](0011-deterministic-lock-ordering-for-deadlock-prevention.md) | 2026-08-07 | **Accepted** | Phase 3B |
| [0012](0012-double-entry-balance-ledger-as-immutable-audit-trail.md) | [Double-Entry Balance Ledger as Immutable Audit Trail](0012-double-entry-balance-ledger-as-immutable-audit-trail.md) | 2026-08-09 | **Accepted** | Phase 3C |
| [0013](0013-flyway-database-migrations-over-ddl-auto-generation.md) | [Flyway Database Migrations Over DDL Auto-Generation](0013-flyway-database-migrations-over-ddl-auto-generation.md) | 2026-08-10 | **Accepted** | Phase 4A |
| [0014](0014-spring-environment-profiles-and-testcontainers-integration-testing-strategy.md) | [Spring Environment Profiles and Testcontainers Integration Testing Strategy](0014-spring-environment-profiles-and-testcontainers-integration-testing-strategy.md) | 2026-08-11 | **Accepted** | Phase 4B |
| [0015](0015-sha-256-request-payload-hashing-and-durable-idempotency-engine.md) | [SHA-256 Request Payload Hashing & Durable Database-Backed Idempotency Engine](0015-sha-256-request-payload-hashing-and-durable-idempotency-engine.md) | 2026-08-15 | **Accepted** | Phase 6A |
| [0016](0016-spring-modulith-event-publication-registry-and-transactional-outbox.md) | [Spring Modulith Event Publication Registry & Transactional Outbox Pattern](0016-spring-modulith-event-publication-registry-and-transactional-outbox.md) | 2026-08-16 | **Accepted** | Phase 6B |
| [0017](0017-stateless-jwt-authentication-and-spring-security-architecture.md) | [Stateless JWT Authentication & Spring Security Architecture](0017-stateless-jwt-authentication-and-spring-security-architecture.md) | 2026-08-17 | **Accepted** | Phase 7A |
| [0018](0018-principal-bound-resource-access-control-and-sender-verification.md) | [Principal-Bound Resource Access Control & Sender Verification](0018-principal-bound-resource-access-control-and-sender-verification.md) | 2026-08-18 | **Accepted** | Phase 7B |
| [0019](0019-declarative-http-interface-client-and-resilient-external-service-integration.md) | [Declarative HTTP Interface Client (RestClient) & Resilient External Service Integration](0019-declarative-http-interface-client-and-resilient-external-service-integration.md) | 2026-08-26 | **Accepted** | Phase 7C |
| [0020](0020-structured-logging-prometheus-metrics-and-opentelemetry-observability.md) | [Structured Logging, Prometheus Metrics & OpenTelemetry Observability Architecture](0020-structured-logging-prometheus-metrics-and-opentelemetry-observability.md) | 2026-08-27 | **Accepted** | Phase 8A |
| [0021](0021-resilience4j-circuit-breaker-per-user-rate-limiting.md) | [Resilience4j Circuit Breaking, Per-User Rate Limiting & Fault-Tolerance Policies](0021-resilience4j-circuit-breaker-per-user-rate-limiting.md) | 2026-09-05 | **Accepted** | Phase 8B |
| [0022](0022-redis-distributed-caching-and-caffeine-fallback.md) | [Redis Distributed Caching and Caffeine Local Fallback Strategy](0022-redis-distributed-caching-and-caffeine-fallback.md) | 2026-09-06 | **Accepted** | Phase 8C |
| [0023](0023-redis-distributed-locking-redisson.md) | [Redis Distributed Locking with Redisson and Fail-Safe Local Fallback](0023-redis-distributed-locking-redisson.md) | 2026-09-07 | **Accepted** | Phase 8D |
| [0024](0024-kafka-event-streaming-spring-modulith.md) | [Kafka Event Streaming via Spring Modulith Event Externalization](0024-kafka-event-streaming-spring-modulith.md) | 2026-09-10 | **Accepted** | Phase 9A |

---

### 🔮 Planned ADRs (Roadmap)

| ADR # | Title | Target Phase | Status |
| :--- | :--- | :--- | :--- |
| `0025` | Gen-AI Spend Categorization with Spring AI and Circuit Breaker Fallback | Phase 10A | Planned |
| `0026` | Java 25 Virtual Threads and Bounded HikariCP Connection Pool Optimization | Phase 10B | Planned |
| `0027` | Multi-Stage Containerization and Full-Stack Local Orchestration with Docker Compose | Phase 11A | Planned |
| `0028` | Cloud-Native Kubernetes Deployment Topology and Horizontal Pod Autoscaling | Phase 11B | Planned |
| `0029` | Automated CI/CD Quality Gates, Coverage Thresholds, and Bytecode Analysis | Phase 11C | Planned |

---

## 🏛️ ADR Process & Guidelines

1. **Format**: Every ADR follows the lightweight format comprising:
   - **Title & Metadata**: Sequence number, title, date, status, and associated development phase.
   - **Context & Problem Statement**: The architectural or engineering challenge being addressed.
   - **Considered Options**: The technical alternatives evaluated with pros and cons.
   - **Decision Outcome & Rationale**: The chosen option and the explicit reasons for selection.
   - **Consequences**: Positive architectural outcomes, trade-offs, and risk mitigations.
2. **Immutability**: Once an ADR is marked as **Accepted**, its content remains immutable. If an architectural decision changes in the future, a new ADR is authored that explicitly supersedes the earlier record.
