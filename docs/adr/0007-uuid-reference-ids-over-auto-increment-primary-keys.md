# ADR-007: UUID Reference IDs Over Auto-Increment Primary Keys in APIs

* **Date**: 2026-08-05
* **Status**: Accepted
* **Phase**: Phase 2E

## Context & Problem Statement
Exposing auto-increment database primary keys (`Long userId`) in external REST URLs (e.g. `/api/v1/users/1`) introduces significant security vulnerabilities:
1. **Resource Enumeration Attacks**: Attackers can sequentially query `/users/1`, `/users/2`, `/users/3` to scrape all system user profiles.
2. **Business Metric Leakage**: Competitors can determine total registered user growth rates by observing sequential ID progression over time.
3. **Internal Key Coupling**: Exposing internal database sequence keys couples external client contracts directly to database storage strategies.

## Considered Options
1. **Expose Auto-Increment Long Primary Keys (`userId`)**: Simple, but vulnerable to enumeration attacks and leaks business growth metrics.
2. **Expose Friendly Handles Only (`upiId`)**: Human-readable (`shashank@kotak`), but handles can change over time as users re-link bank accounts.
3. **Dual Identification Strategy (Internal `userId`, External `referenceId` UUID)**: Retain `Long userId` internally for fast database foreign key joins and indexing, but assign a non-enumerable `UUID referenceId` for all API responses and URL routing.

## Decision Outcome
Chosen Option: **Dual Identification Strategy (Internal `userId`, External `referenceId` UUID)**

### Rationale
* **Security & Privacy**: Cryptographically pseudo-random UUID v4 strings prevent resource enumeration attacks and hide user creation counts.
* **Domain Flexibility**: `upiId` remains the friendly human-readable handle (`shashank@kotak`), while `referenceId` serves as the immutable internal system reference.
* **Performance**: High-performance SQL joins continue to utilize numeric `BIGINT` primary/foreign keys (`user_id`), avoiding string join performance overhead in PostgreSQL.

## Consequences
* **Positive**: Complete insulation against resource enumeration attacks, non-leaky API contracts, optimized database joins.
* **Negative / Trade-offs**: Entities require a `@PrePersist` hook or column default to generate UUIDs upon creation.
* **Risks & Mitigations**: Ensure secondary unique index (`idx_users_reference_id`) is maintained on `referenceId`.
