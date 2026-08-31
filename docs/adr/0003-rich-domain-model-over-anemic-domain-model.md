# ADR-003: Rich Domain Model Over Anemic Domain Model

* **Date**: 2026-08-01
* **Status**: Accepted
* **Phase**: Phase 2A

## Context & Problem Statement
Anemic domain models treat JPA entities as simple data bags with getters and setters, scattering business invariants (such as non-negative balance checks and debit rules) across multiple service classes.

## Considered Options
1. **Anemic Domain Model**: Entities hold only state; services perform all business logic and validations.
2. **Rich Domain Model**: Entities encapsulate state alongside domain behaviors and invariant checks (`debit()`, `credit()`).

## Decision Outcome
Chosen Option: **Rich Domain Model**

### Rationale
Rich domain entities encapsulate domain invariants directly within entity boundaries (`User.debit()`, `User.credit()`), preventing invalid domain state transitions (e.g. negative balances or invalid debit amounts) regardless of caller invocation path.

## Consequences
* **Positive**: High cohesion, self-validating entities, reusable business logic across multiple services.
* **Negative / Trade-offs**: Entities must remain decoupled from infrastructure concerns (repositories, external APIs).
* **Risks & Mitigations**: Keep entity methods focused strictly on state invariants; delegate orchestration to domain services.
