# ADR-016: Spring Modulith Event Publication Registry & Transactional Outbox Pattern

* **Date**: 2026-08-16
* **Status**: Accepted
* **Phase**: Phase 6B

## Context & Problem Statement
When a payment transaction completes, domain events (such as `TransferCompletedEvent`) must be published for asynchronous auditing, notifications, and downstream processing (e.g. Kafka streaming in Phase 9). Directly publishing events over network brokers inside a `@Transactional` business method causes the **dual-write problem**: if the broker call fails after DB commit, or if the DB transaction aborts after message dispatch, the systems fall out of sync.

## Considered Options
1. **Direct Synchronous Broker Publishing (e.g. Kafka/RabbitMQ in `@Transactional`)**: Vulnerable to the dual-write problem, network latency, and distributed transaction anomalies.
2. **Hand-Rolled Outbox Table with Polling Dispatcher**: Requires custom schema, custom polling workers with advisory locks, dead-letter retry queues, and significant maintenance overhead.
3. **Spring Modulith Event Publication Registry (`spring-modulith-starter-jpa`)**: Framework-managed transactional outbox using `ApplicationEventPublisher`. Events published within `@Transactional` are atomically recorded in an `event_publication` log table in the same database transaction. Async event listeners annotated with `@ApplicationModuleListener` execute outside the publishing transaction and mark the registry entry as completed.

## Decision Outcome
Chosen Option: **Spring Modulith Event Publication Registry (`spring-modulith-starter-jpa`)**

### Rationale
* **Zero Dual-Write Problem**: Events are persisted to PostgreSQL within the exact same ACID transaction as the wallet balance mutations and double-entry ledger entries.
* **Transactional Decoupling**: Downstream consumers (`@ApplicationModuleListener`) run in separate asynchronous transactions after commit. Failures in consumers do not fail or roll back the completed financial transfer.
* **Automatic Event Replay & Resilience**: On application restart or retry, uncompleted events in `event_publication` can be re-dispatched (`republish-outstanding-events-on-restart: true`).
* **Architectural Boundary Enforcement**: Spring Modulith provides compile-time / test-time architectural boundary verification (`ApplicationModules.verify()`), ensuring clean modular domain encapsulation without premature microservices decomposition.
* **Zero Custom Boilerplate**: Eliminates hundreds of lines of custom polling daemon and state-tracking code.

## Consequences
* **Positive**: ACID atomicity for domain events, seamless future externalization to Kafka (Phase 9A), built-in architectural verification.
* **Negative / Trade-offs**: Requires `event_publication` database table managed via Flyway (`V6__create_event_publication_registry.sql`).
