# ADR-018: Principal-Bound Resource Access Control & Sender Verification

* **Date**: 2026-08-18
* **Status**: Accepted
* **Phase**: Phase 7B

## Context & Problem Statement
In a payment system, authentication (Phase 7A) establishes identity via JWT bearer tokens, but does not inherently restrict which resources the authenticated principal can mutate or inspect. Without explicit fine-grained authorization:
1. An authenticated user `alice@payflow` could submit a transfer request with `senderUpiId: bob@payflow`, fraudulently debiting Bob's balance (Impersonation / Unauthorized Debit).
2. An authenticated user could inspect another user's balance ledger entries (`GET /api/v1/users/{id}/ledger`) or transaction history (`GET /api/v1/transactions/{id}`), violating user data privacy and banking secrecy regulations.

## Considered Options
1. **Method-Level SpEL Expressions (`@PreAuthorize`) Only**: Declarative Spring Security annotations using SpEL (e.g. `@PreAuthorize("#request.senderUpiId == authentication.name")`). While clean for simple rules, SpEL lacks compile-time type safety, does not easily handle multi-party data visibility (sender OR receiver), and makes unit testing without Spring context heavier.
2. **Filter-Level Role Checks Only**: Restricting endpoints purely by role (`ROLE_USER`, `ROLE_ADMIN`). Fails to enforce resource ownership because all authenticated users share `ROLE_USER`.
3. **Defense-in-Depth Principal-Bound Authorization (Chosen)**:
   - Combine Spring Security `AccessDeniedHandler` (RFC 7807 problem details) for filter-level denials.
   - Enforce programmatic domain-level ownership and participant verification in service and controller layers using `SecurityUtils.getAuthenticatedUpiId()`.
   - Map unauthorized attempts to `ForbiddenOperationException` (`403 Forbidden`).

## Decision Outcome
Chosen Option: **Defense-in-Depth Principal-Bound Authorization (Option 3)**

### Rationale
* **Sender Verification on Transfers**: `TransactionService.sendMoney()` asserts `authenticatedUpi.equalsIgnoreCase(request.getSenderUpiId())`. Discrepancies immediately throw `ForbiddenOperationException`, rejecting the transfer before acquiring row locks or debiting balances.
* **Multi-Party Transaction Visibility**: `TransactionService.getTransactionByReferenceId()` ensures that only the **sender** or the **receiver** can view transaction details. Third-party users receive `403 Forbidden`.
* **Double-Entry Balance Ledger Privacy**: `UserService.getUserLedger()` enforces that users can only retrieve ledger entries associated with their own `referenceId` / `upiId`.
* **Profile Ownership**: `UserController.getUserById()` and `getUserByUpiId()` enforce that users can only fetch their own profile details.
* **Uniform Error Standard**: All authorization violations return standardized RFC 7807 `403 Forbidden` (`application/problem+json`) with type `https://api.payflow.com/errors/forbidden-operation` and clear non-leaking error messages.
* **Filter-Level Fallback**: `JwtAccessDeniedHandler` guarantees that any Spring Security framework-level authorization rejection also emits RFC 7807 problem details.

## Consequences
* **Positive**: Guaranteed protection against unauthorized transfers, data exfiltration, and account impersonation; complete test isolation with zero flaky mock requirements.
* **Negative / Trade-offs**: Requires extracting and verifying `SecurityUtils` principal across private query and mutation service methods.
