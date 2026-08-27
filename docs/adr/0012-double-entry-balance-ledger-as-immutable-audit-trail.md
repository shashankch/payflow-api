# ADR-012: Double-Entry Balance Ledger as Immutable Audit Trail

* **Date**: 2026-08-09
* **Status**: Accepted
* **Phase**: Phase 3C

## Context & Problem Statement
Directly mutating `users.balance` without an explicit ledger record creates financial audit risks: if a balance value becomes corrupted or disputed, there is no immutable audit trail to reconstruct the historical sequence of balance states or verify financial integrity.

## Considered Options
1. **Single Balance Field Mutation (`users.balance`)**: Simple, but lacks historical auditability and makes financial balance reconciliation impossible.
2. **Double-Entry Balance Ledger (`balance_ledger` table)**: Write two immutable ledger entries (`DEBIT` for sender, `CREDIT` for receiver) capturing `amount`, `balanceBefore`, and `balanceAfter` inside the same `@Transactional` database boundary as the money transfer.

## Decision Outcome
Chosen Option: **Double-Entry Balance Ledger (`balance_ledger` table)**

### Rationale
* **Financial Auditability**: Every money movement creates two immutable ledger entries documenting exact balance state changes before and after execution.
* **Reconciliation Support**: `users.balance` acts as a high-performance denormalized field; the actual source of financial truth can be reconstructed at any time using a `SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE -amount END)` SQL aggregate query over the `balance_ledger` table.
* **ACID Atomicity**: Sender debit, receiver credit, transaction record, and both ledger entries execute in a single database transaction.

## Consequences
* **Positive**: Complete audit trail, balance reconstruction capability, financial compliance.
* **Negative / Trade-offs**: Increases database row writes per transaction from 3 to 5 (2 user updates, 1 transaction record, 2 ledger records).
