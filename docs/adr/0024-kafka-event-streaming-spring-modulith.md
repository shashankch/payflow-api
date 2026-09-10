# ADR-024: Kafka Event Streaming via Spring Modulith Event Externalization

* **Date**: 2026-09-10
* **Status**: Accepted
* **Phase**: Phase 9A

## Context & Problem Statement
In a distributed financial architecture, completed monetary transactions trigger a multitude of downstream business workflows, including fraud detection, notification dispatching, regulatory audit logging, and spend analytics. 

Prior to Phase 9A, Payflow API utilized Spring Modulith's Event Publication Registry (introduced in Phase 6B) for in-process asynchronous domain event publication (`TransferCompletedEvent`). While in-process `@ApplicationModuleListener` listeners provide transactional durability via the database outbox table (`event_publication`), they operate entirely within a single application instance.

To scale out independent downstream microservices and data pipelines, these domain events must be externalized to a distributed messaging backbone (**Apache Kafka**). However, naive event publishing introduces significant distributed systems risks:
1. **Dual-Write Vulnerability**: If application code writes to PostgreSQL and directly calls `KafkaTemplate.send()` within `@Transactional`, network partitions or broker latency can lead to uncommitted database changes broadcasting phantom messages, or committed transactions failing to publish events.
2. **Domain Service Code Pollution**: Injecting messaging infrastructure (`KafkaTemplate`, topic names, serializers) into core financial services (`TransactionService`) violates single responsibility and clean architecture principles.
3. **Partition Ordering Violations**: Without deterministic message partitioning, transactions from the same account could be distributed across different Kafka partitions, breaking chronological ordering and ledger auditability for downstream consumers.
4. **End-to-End Delivery Semantics**: An outbox-to-Kafka publisher cannot provide end-to-end exactly-once semantics without distributed 2PC or consumer-side deduplication. A node crash occurring after Kafka acknowledges a write but before the local outbox row is updated in PostgreSQL results in event re-transmission upon restart. The system must guarantee at-least-once delivery while enabling consumer deduplication.
5. **Local Developer Friction**: Forcing every developer and CI unit test run to maintain an active Kafka broker drastically slows down feedback loops and increases onboarding complexity.

## Considered Options
1. **Direct Kafka Publishing (`KafkaTemplate` in Service Layer)**:
   - *Pros*: Simple to implement initially.
   - *Cons*: Vulnerable to dual-write failures; breaks transactional atomicity between database state and event bus; tightly couples domain logic to Kafka.
2. **Change Data Capture (CDC via Debezium & Kafka Connect)**:
   - *Pros*: Completely transparent database log tailing.
   - *Cons*: High operational overhead; requires maintaining Kafka Connect clusters, Debezium connector configurations, and schema registry synchronizations.
3. **Custom Polling Outbox Dispatcher (`SELECT ... FOR UPDATE SKIP LOCKED`)**:
   - *Pros*: Keeps transactional atomicity inside PostgreSQL.
   - *Cons*: Requires custom polling worker threads, manual retry backoff, death-letter handling, and database IO churn under high write volume.
4. **Spring Modulith Event Externalization with Fail-Safe Local Fallback (Chosen)**:
   - Uses `spring-modulith-events-kafka` to automatically bridge events from the existing transactional outbox registry to Kafka topics upon successful database transaction commit.
   - Domain services remain 100% agnostic of Kafka, continuing to publish domain events via Spring's standard `ApplicationEventPublisher`.
   - Event records are annotated with `@Externalized`, and routing is configured programmatically via `EventExternalizationConfiguration`, dynamically binding to `${payflow.kafka.transfers-topic}` and partitioning by sender UPI (`it.senderUpi()`).
   - Producer configured with `acks=all` and `enable.idempotence=true` for producer-side retry deduplication within producer sessions.
   - End-to-end pipeline guarantees **at-least-once delivery**; downstream consumers achieve effective exactly-once processing by keying on the event's immutable `referenceId`.
   - Profile-conditional: Enabled in `prod` and `kafka` profiles (via dedicated `application-kafka.yml`), while disabled in `local`, `test`, and `prod-light` (`spring.modulith.events.externalization.enabled: false`), allowing non-prod environments to execute lightweight in-process event listeners without a Kafka broker.

## Decision Outcome
Chosen Option: **Spring Modulith Event Externalization (Option 4)**

### Architectural Design & Mechanics

#### 1. Domain Event & Dynamic Topic Routing
In `TransferCompletedEvent.java`:
```java
@Externalized
public record TransferCompletedEvent(
    UUID referenceId,
    String senderUpi,
    String receiverUpi,
    BigDecimal amount,
    TransactionStatus status,
    BigDecimal senderAfter,
    BigDecimal receiverAfter,
    Instant timestamp
) {}
```

In `KafkaConfig.java`:
```java
@Bean
public EventExternalizationConfiguration eventExternalizationConfiguration(
        @Value("${payflow.kafka.transfers-topic:payflow.transfers}") String topicName) {
    return EventExternalizationConfiguration.externalizing()
            .select(EventExternalizationConfiguration.annotatedAsExternalized())
            .route(TransferCompletedEvent.class, it -> RoutingTarget.forTarget(topicName).andKey(it.senderUpi()))
            .build();
}
```
- **Unified Topic Resolution**: Both the `NewTopic` bean and the event externalizer resolve `${payflow.kafka.transfers-topic}`, ensuring topic creation and routing targets are always synchronized.
- **Partition Key**: `senderUpi`. Guarantees that all transfer events initiated by a given user are written to the exact same Kafka partition, preserving strict chronological ordering.

#### 2. Transactional Outbox Flow
```
Client Request (POST /api/v1/transactions)
  ├── 1. Acquire DB Row Locks (Pessimistic Write)
  ├── 2. Mutate Balances & Append BalanceLedgerEntry
  ├── 3. eventPublisher.publishEvent(new TransferCompletedEvent(...))
  │      └── Spring Modulith writes event to event_publication table
  ├── 4. Transaction COMMITS in PostgreSQL
  └── 5. Spring Modulith Post-Commit Listener:
         ├── If prod/kafka profile active:
         │     └── Sends event to Kafka (topic: payflow.transfers, key: senderUpi)
         │     └── Marks event_publication record as completed in DB
         └── In-process @ApplicationModuleListener executes asynchronously
```

#### 3. Infrastructure, Replication & Delivery Guarantees
- **Topic Configuration (`KafkaConfig.java`)**:
  - `@Profile({"prod", "kafka"})`
  - Partitions: Configurable via `payflow.kafka.topic-partitions` (default 3).
  - Replication Factor: Configurable via `payflow.kafka.topic-replicas`. Defaults to `3` in `application-prod.yml` (`${PAYFLOW_KAFKA_REPLICAS:3}`) for production multi-broker resilience, and `1` in `application-kafka.yml` for single-broker dev/test environments.
- **Producer Configuration (`application-prod.yml` and `application-kafka.yml`)**:
  - `acks = all`: Producer requires acknowledgement from all in-sync replicas before marking send successful.
  - `enable.idempotence = true`: Eliminates duplicate writes resulting from network retries within an active producer session.
  - `key-serializer`: `StringSerializer` (UPI handle).
  - `value-serializer`: `JsonSerializer` (Jackson-serialized event payload).
- **At-Least-Once Delivery Semantics**:
  - The outbox handoff to Kafka guarantees at-least-once delivery. If the process crashes after Kafka acknowledges the record but before Spring Modulith updates `event_publication.completion_date`, the outstanding event is republish-eligible upon restart.
  - Downstream consumers must be idempotent and deduplicate incoming events using `referenceId`.
- **Fail-Safe Local Fallback**:
  - `application.yml` disables externalization by default (`spring.modulith.events.externalization.enabled: false`).
  - Activating `--spring.profiles.active=kafka` activates `application-kafka.yml`, which enables externalization (`enabled: true`) and configures Kafka endpoints.
  - Local developers running with default profile enjoy sub-second startup with zero Kafka dependencies; events are delivered in-process via `TransferEventListener`.

## Consequences

### Positive Outcomes
* **Zero Dual-Write Hazard**: Messages are only published to Kafka if and only if the underlying database transaction successfully commits.
* **Domain Purity**: Not a single line of Kafka-specific code exists within `TransactionService` or domain entities.
* **Deterministic In-Order Consumption**: Partitioning by sender UPI guarantees that all debit events for an account maintain strict timeline integrity.
* **Accurate Delivery Model**: Explicitly documents at-least-once outbox delivery with producer retry deduplication.
* **Production HA Compatibility**: Configurable replication factor ensures topics created in multi-broker production clusters satisfy high availability requirements.

### Trade-offs & Mitigations
* **At-Least-Once Duplicate Handling**: If a crash occurs prior to updating the outbox table, republishing can produce duplicates across process restarts. *Mitigation*: Downstream consumer services must maintain an idempotent inbox table keying on `referenceId`.
* **Outbox Storage Growth**: High transfer volumes increase rows in `event_publication`. *Mitigation*: Spring Modulith's automated completion cleaner periodically purges completed events.
