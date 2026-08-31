# ADR-008: Spring Modulith Modular Monolith Over Distributed Microservices

* **Date**: 2026-08-07
* **Status**: Accepted
* **Phase**: Phase 6B (Architecture Review)

## Context & Problem Statement
Payflow processes peer-to-peer financial transfers where a single operation involves debiting the sender, crediting the receiver, recording a transaction, writing ledger entries, and publishing domain events. Splitting these into separate microservices (e.g., `User Service`, `Transfer Service`, `Ledger Service`) would lose local database ACID transactions, requiring distributed saga orchestration or two-phase commit (2PC) to maintain consistency. This introduces significant complexity, dual-write bugs, and split-brain risks before the domain rules are even stable.

## Considered Options
1. **Distributed Microservices (HTTP/gRPC)**: Each domain concern runs as a separate deployable service. Requires distributed transactions (sagas, 2PC) for cross-service state consistency.
2. **Plain Monolith (No Module Boundaries)**: Single deployable with no enforced package boundaries. Simple but leads to spaghetti coupling as the codebase grows.
3. **Spring Modulith Modular Monolith**: Single deployable with strict, compile-time-verified module boundaries. Uses Spring's `ApplicationEventPublisher` for inter-module communication. Evolves to Kafka event streaming via `spring-modulith-events-kafka` without domain code changes.

## Decision Outcome
Chosen Option: **Spring Modulith Modular Monolith**

### Rationale
* **ACID Safety**: Sender debit, receiver credit, transaction record, ledger entries, and domain event all execute in a single `@Transactional` database transaction. Zero distributed transaction overhead.
* **Enforced Boundaries**: `ApplicationModules.of(PayflowApiApplication.class).verify()` test validates strict package encapsulation at compile time, preventing accidental cross-module coupling.
* **Event Publication Registry**: Spring Modulith's `spring-modulith-starter-jpa` persists domain events (e.g., `TransferCompletedEvent`) to an `event_publication` table atomically within the same DB transaction. This is a framework-managed transactional outbox.
* **Evolutionary Path**: Adding `spring-modulith-events-kafka` in Phase 9A auto-externalizes events to Kafka topics with zero changes to `TransactionService` domain code. The service continues to call `applicationEventPublisher.publishEvent()` — the framework bridges to Kafka transparently.
* **Architectural Simplicity**: Avoids premature microservice distribution and preserves single-database transaction boundaries until physical service isolation is explicitly required.

## Consequences
* **Positive**: Single-database ACID safety, zero distributed transaction complexity, compile-time boundary enforcement, seamless Kafka evolution path.
* **Negative / Trade-offs**: All modules share a single database and JVM. Horizontal scaling is per-application-instance, not per-module.
* **Risks & Mitigations**: If individual modules need independent scaling (unlikely at Payflow's scale), Spring Modulith modules can be extracted to standalone services along their already-enforced API boundaries.
