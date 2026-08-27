# ADR-001: Use BigDecimal for All Monetary Values

* **Date**: 2026-08-01
* **Status**: Accepted
* **Phase**: Phase 2A

## Context & Problem Statement
Floating-point primitive types (`double`, `float`) use IEEE 754 binary representation, which cannot precisely represent base-10 decimals (e.g., `0.1 + 0.2 = 0.30000000000000004`). In financial applications, accumulated rounding errors compromise ledger integrity and result in monetary discrepancy.

## Considered Options
1. **IEEE 754 Floating-Point (`Double`/`float`)**: Fast, built-in, but causes imprecise rounding error.
2. **Integer Cent/Sub-unit Amounts (`Long` cents)**: Precise, but awkward when dealing with fractional currency fractions or dynamic currency precision.
3. **Java `BigDecimal`**: Arbitrary-precision signed decimal numbers, explicitly suited for financial calculations.

## Decision Outcome
Chosen Option: **Java `BigDecimal`**

### Rationale
`BigDecimal` provides exact precision representation for decimal currency values with configurable scale (`precision = 19, scale = 4` in database mapping) and explicit rounding modes (`RoundingMode.HALF_EVEN`).

## Consequences
* **Positive**: Zero floating-point rounding errors in monetary balance arithmetic and ledger entries.
* **Negative / Trade-offs**: Slightly higher memory overhead and minor performance cost compared to native double primitives.
* **Risks & Mitigations**: Always specify scale and explicit rounding mode (`RoundingMode.HALF_EVEN`) when performing division or scale adjustments.
