# ADR-010: Pessimistic Locking for High-Concurrency Balance Operations

* **Date**: 2026-08-07
* **Status**: Accepted
* **Phase**: Phase 3B

## Context & Problem Statement
Money transfers require atomic balance updates (`sender.debit()`, `receiver.credit()`). Under concurrent execution, read-then-write patterns without explicit database locking lead to race conditions and lost updates (double-spending).

## Considered Options
1. **Optimistic Locking (`@Version`)**: Detects concurrent modifications at commit time and throws `OptimisticLockException`. Requires retry loops in application code. Under high write contention (e.g. popular merchants receiving hundreds of transfers per second), optimistic locking results in high abort rates.
2. **Pessimistic Write Locking (`SELECT ... FOR UPDATE`)**: Acquires exclusive row-level database locks when reading user balances inside the transaction boundary. Subsequent concurrent transactions attempting to read/lock the same account block until the holding transaction commits or rolls back.

## Decision Outcome
Chosen Option: **Pessimistic Write Locking (`SELECT ... FOR UPDATE`)**

### Rationale
* **Guaranteed Consistency**: Exclusive row locks prevent concurrent reads of stale balances during active transfers, eliminating double-spending race conditions.
* **Predictable Execution**: Transactions execute sequentially per account without triggering application-level retry loops or transaction abort spikes under high write contention.

## Consequences
* **Positive**: Guaranteed ACID balance integrity, zero double-spend window.
* **Negative / Trade-offs**: Concurrent transfers targeting the same account wait on database row locks, increasing database connection hold times under load.
* **Risks & Mitigations**: Set explicit `@Transactional(timeout = 5)` transaction timeouts to prevent lock wait deadlocks from holding connection pool resources indefinitely.
