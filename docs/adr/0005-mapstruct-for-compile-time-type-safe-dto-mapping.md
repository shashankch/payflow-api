# ADR-005: MapStruct for Compile-Time Type-Safe DTO Mapping

* **Date**: 2026-08-02
* **Status**: Accepted
* **Phase**: Phase 2C

## Context & Problem Statement
Manual object mapping between entities and DTOs introduces boilerplate code, increases maintenance overhead as domain models expand, and is prone to human error (such as missed field copies). Runtime reflection mapping frameworks (e.g. ModelMapper) introduce non-trivial performance latency and hide field type mismatches until runtime execution.

## Considered Options
1. **Manual Mapping Methods**: Writing custom `toEntity()` / `toResponse()` conversion code in controllers or static factory methods. Highly performant, but repetitive and boilerplate-heavy.
2. **Runtime Reflection Mappers (ModelMapper, Dozer)**: Automated mapping via reflection, but incurs runtime CPU overhead and hides mapping errors until runtime execution.
3. **Compile-Time Code Generation (MapStruct)**: Annotation processor generates plain, un-reflected Java byte-code at compile time with type checking and zero runtime performance penalty.

## Decision Outcome
Chosen Option: **MapStruct (Compile-Time Generation)**

### Rationale
* **Zero Runtime Overhead**: MapStruct generates plain Java method calls during compilation; no reflection is executed at runtime.
* **Compile-Time Verification**: Unmapped target properties or mismatched types raise immediate compiler errors rather than silent runtime failures.
* **Spring Integration**: Native integration with Spring Dependency Injection (`componentModel = "spring"`), allowing mappers to be injected clean into `@RestController` components.

## Consequences
* **Positive**: Blazing-fast performance (identical to handwritten code), strict compile-time type checking, clean controller code.
* **Negative / Trade-offs**: Requires annotation processor configuration (`mapstruct-processor`) in Maven `pom.xml`.
* **Risks & Mitigations**: Ensure `maven-compiler-plugin` includes `mapstruct-processor` in its `annotationProcessorPaths`.
