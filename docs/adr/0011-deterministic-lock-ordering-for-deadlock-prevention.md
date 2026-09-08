# ADR-011: Deterministic Lock Ordering for Deadlock Prevention

* **Date**: 2026-08-07
* **Status**: Accepted
* **Phase**: Phase 3B

## Context & Problem Statement
When acquiring pessimistic write locks on two database rows (sender and receiver accounts), non-deterministic lock acquisition order causes database deadlocks under concurrent cross-transfers (e.g. Tx 1: Aarav sends to Priya; Tx 2: Priya sends to Aarav). Tx 1 locks Aarav then waits for Priya; Tx 2 locks Priya then waits for Aarav, resulting in a cyclical lock dependency deadlock.

## Considered Options
1. **Application Lock Ordering (Sender First, Receiver Second)**: Simple, but vulnerable to deadlocks whenever reciprocal transfers execute concurrently.
2. **Deterministic Lock Ordering (Alphabetical by UPI ID)**: Sort sender and receiver UPI IDs lexicographically prior to lock acquisition. Both Tx 1 and Tx 2 acquire locks in the exact same sequence (`aarav@payflow` first, then `priya@payflow`).

## Decision Outcome
Chosen Option: **Deterministic Lock Ordering (Alphabetical by UPI ID)**

### Rationale
* **Deadlock Elimination**: Strictly ordering lock requests prevents cyclical wait graphs at the database level. Both reciprocal transfers attempt to lock `aarav@payflow` first; the second transaction cleanly blocks until the first completes.
* **Zero Overhead**: Sorting two string references in memory takes negligible time (<1 microsecond).

## Consequences
* **Positive**: Eliminates database deadlocks during reciprocal concurrent money transfers.
* **Negative / Trade-offs**: Requires minor mapping logic to re-assign `sender` and `receiver` domain entity references after acquiring locks in sorted order.
