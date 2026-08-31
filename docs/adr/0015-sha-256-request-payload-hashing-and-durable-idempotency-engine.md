# ADR-015: SHA-256 Request Payload Hashing & Durable Database-Backed Idempotency Engine

* **Date**: 2026-08-15
* **Status**: Accepted
* **Phase**: Phase 6A

## Context & Problem Statement
In peer-to-peer payment APIs, network timeouts, client reconnections, and gateway retries frequently cause duplicate HTTP `POST` mutation requests. Without strict idempotency controls, a client retrying a transfer could execute duplicate balance debits and transfers.

## Considered Options
1. **In-Memory Cache (e.g. Guava/Caffeine)**: Fast, but lost on application restart and cannot be shared across multiple backend server instances.
2. **Distributed Redis Cache**: Low latency, but adds operational infrastructure complexity and risks split-brain/data loss if Redis restarts without AOF persistence.
3. **Durable Database-Backed Registry with SHA-256 Payload Hashing**: Store idempotency records in a dedicated PostgreSQL table (`idempotency_registry`), verified with SHA-256 cryptographic digests, with background scheduled TTL cleanup.

## Decision Outcome
Chosen Option: **Durable Database-Backed Registry with SHA-256 Payload Hashing**

### Rationale
* **Zero Double-Spending Guarantee**: Storing records in PostgreSQL ensures ACID durability across node restarts, horizontal scaling, and transactional isolation.
* **Payload Tampering & Reuse Prevention**: Computing a deterministic SHA-256 hash of the raw HTTP request bytes prevents fraudulent client key reuse with modified amounts or recipient UPIs.
* **In-Flight Conflict Detection & Crash Lease Recovery**: Status tracking (`PROCESSING` / `INITIATED`) detects concurrent requests with the same key and rejects them with `409 Conflict`. An in-flight lease expiration window (2 minutes) ensures that orphaned in-flight states from crashed worker nodes automatically unlock for client retries without waiting for the 24-hour TTL purge.
* **Distributed Race Protection**: Simultaneous key inserts on multi-node deployments catching `DataIntegrityViolationException` gracefully map to `409 Conflict`.
* **Cached Replay**: Completed requests (`SUCCESS`) immediately replay the cached HTTP response code and response JSON without re-executing backend balance changes.
* **Performance**: An index on `created_at` (`idx_idemp_created`) guarantees rapid scheduled TTL purge queries without scanning the entire registry table.

## Consequences
* **Positive**: Absolute protection against duplicate payments, standard financial industry compliance (Stripe/Adyen pattern), zero external infrastructure dependencies, resilience to worker node crashes.
* **Negative / Trade-offs**: Requires database round-trips for mutation requests; requires periodic TTL purge job.
