# ADR-002: Constructor Injection Over Field Injection

* **Date**: 2026-08-01
* **Status**: Accepted
* **Phase**: Phase 2A

## Context & Problem Statement
Field injection via `@Autowired` tightly couples Spring components to the Spring DI container, prevents `final` immutable fields, hides component dependencies, and makes unit testing difficult without starting Spring contexts or using reflection.

## Considered Options
1. **Field Injection (`@Autowired private Service service`)**: Convenient syntax, but hides dependencies and inhibits immutability/testability.
2. **Setter Injection**: Allows optional dependencies, but enables mutable component state post-construction.
3. **Constructor Injection**: Explicitly declares required dependencies as `final` parameters.

## Decision Outcome
Chosen Option: **Constructor Injection**

### Rationale
Constructor injection enforces immutability (`final` fields), guarantees all required dependencies are provided at instantiation time, simplifies unit testing without Spring context spinners, and aligns with Spring Framework best practices.

## Consequences
* **Positive**: Clean component testability, immutability guarantees, static dependency verification at compile time.
* **Negative / Trade-offs**: Slightly more boilerplate constructor code (mitigated by explicit standard constructors).
* **Risks & Mitigations**: Circular dependency detection occurs at startup (which is desirable as it indicates architectural smell).
