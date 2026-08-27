# ADR-006: RFC 7807 ProblemDetail & Centralized Exception Handling

* **Date**: 2026-08-03
* **Status**: Accepted
* **Phase**: Phase 2D

## Context & Problem Statement
Without centralized exception handling, uncaught runtime exceptions return default Spring Whitelabel 500 HTML pages or raw Java stack traces, leaking internal system implementation details, security vulnerabilities, and database query structures. Furthermore, non-standardized error JSON responses force frontends and API consumers to write ad-hoc error handling logic for different endpoints.

## Considered Options
1. **Default Spring Boot Whitelabel / ErrorController**: Simple, but exposes HTML or unstandardized JSON responses without consistent error schema.
2. **Custom DTO Error Response Class**: Custom Java class. Standardized within the application, but non-compliant with open web standards.
3. **RFC 7807 `ProblemDetail` via `@RestControllerAdvice`**: Native Spring Boot standard (`org.springframework.http.ProblemDetail`) defining structured HTTP error responses with `type`, `title`, `status`, `detail`, `instance`, `timestamp`, and `X-Request-Id` correlation tracking.

## Decision Outcome
Chosen Option: **RFC 7807 `ProblemDetail` via `@RestControllerAdvice`**

### Rationale
* **Industry Standard**: RFC 7807 provides a universally recognized format (`application/problem+json`) understood natively by modern API clients and gateways.
* **Zero Information Leakage**: All uncaught exceptions are intercepted and sanitized to generic 500 responses (`"An unexpected internal error occurred"`), preventing stack trace leaks.
* **Field-Level Validation Reporting**: DTO validation errors (`MethodArgumentNotValidException`) provide structured `errors` maps with status `422 Unprocessable Entity`.
* **Request Correlation**: Integrated with `RequestIdFilter` (`X-Request-Id` in MDC) so every error response includes the request correlation ID for distributed tracing.

## Consequences
* **Positive**: Consistent API error contract, enhanced security, production-grade observability and correlation tracing.
* **Negative / Trade-offs**: Custom exceptions must be mapped in `@RestControllerAdvice`.
* **Risks & Mitigations**: Ensure all domain services throw specific `PayflowException` subtypes rather than generic runtime exceptions.
