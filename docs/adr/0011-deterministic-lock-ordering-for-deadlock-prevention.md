# ADR-011: Deterministic Lock Ordering for Deadlock Prevention

* **Date**: 2026-08-07
* **Status**: Accepted
* **Phase**: Phase 3B

## Context & Problem Statement
When acquiring pessimistic write locks on two database rows (sender and receiver accounts), non-deterministic lock acquisition order causes database deadlocks under concurrent cross-transfers (e.g. Tx 1: Alice sends to Bob; Tx 2: Bob sends to Alice). Tx 1 locks Alice then waits for Bob; Tx 2 locks Bob then waits for Alice, resulting in a cyclical lock dependency deadlock.

## Considered Options
1. **Application Lock Ordering (Sender First, Receiver Second)**: Simple, but vulnerable to deadlocks whenever reciprocal transfers execute concurrently.
2. **Deterministic Lock Ordering (Alphabetical by UPI ID)**: Sort sender and receiver UPI IDs lexicographically prior to lock acquisition. Both Tx 1 and Tx 2 acquire locks in the exact same sequence (`alice@payflow` first, then `bob@payflow`).

## Decision Outcome
Chosen Option: **Deterministic Lock Ordering (Alphabetical by UPI ID)**

### Rationale
* **Deadlock Elimination**: Strictly ordering lock requests prevents cyclical wait graphs at the database level. Both reciprocal transfers attempt to lock `alice@payflow` first; the second transaction cleanly blocks until the first completes.
* **Zero Overhead**: Sorting two string references in memory takes negligible time (<1 microsecond).

## Consequences
* **Positive**: Eliminates database deadlocks during reciprocal concurrent money transfers.
* **Negative / Trade-offs**: Requires minor mapping logic to re-assign `sender` and `receiver` domain entity references after acquiring locks in sorted order.
