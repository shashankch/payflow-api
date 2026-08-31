# ADR-009: OpenTelemetry Distributed Tracing via Micrometer Bridge

* **Date**: 2026-08-07
* **Status**: Accepted
* **Phase**: Phase 8A

## Context & Problem Statement
Payflow needs distributed tracing to correlate requests across HTTP controllers, database queries, async event listeners, and (in production) Kafka consumers. The existing `X-Request-Id` MDC pattern (Phase 2D) provides basic request correlation but does not follow W3C trace propagation standards, making it incompatible with industry-standard tracing backends (Grafana Tempo, Jaeger, Zipkin).

## Considered Options
1. **Custom `X-Request-Id` only (Current State)**: Simple UUID injected via `RequestIdFilter`. No W3C standard compliance, no automatic span propagation across async boundaries.
2. **Vendor-Specific Tracing SDK (e.g., Datadog, New Relic)**: Proprietary SDKs with deep integration but vendor lock-in and paid tiers.
3. **Micrometer Tracing + OpenTelemetry Bridge**: Uses `micrometer-tracing-bridge-otel` to bridge Spring Boot's native Micrometer Observation API to the OpenTelemetry SDK. Exports traces via OTLP to any compatible backend. W3C `traceparent` propagation standard.

## Decision Outcome
Chosen Option: **Micrometer Tracing + OpenTelemetry Bridge**

### Rationale
* **W3C Standard**: `traceparent` headers (`traceId`, `spanId`) are automatically injected into HTTP requests, database queries, and async thread pools. Compatible with any OTLP backend.
* **Zero Vendor Lock-in**: Micrometer Tracing is the Spring Boot native abstraction. The OTel bridge can export to Grafana Tempo (free/open-source), Jaeger, Zipkin, or any commercial APM.
* **Coexistence with X-Request-Id**: The existing `RequestIdFilter` and MDC `requestId` are preserved. Both `requestId` and `traceId` appear in structured log output, providing layered correlation.
* **Automatic Instrumentation**: `@Observed` annotation on service methods (e.g., `sendMoney()`) creates spans with business-relevant names and tags automatically.
* **All Open-Source**: `micrometer-tracing-bridge-otel` and `opentelemetry-exporter-otlp` are fully open-source with no paid tiers.

## Consequences
* **Positive**: Industry-standard distributed tracing, portable across backends, automatic span propagation, zero vendor lock-in.
* **Negative / Trade-offs**: Additional dependencies (`micrometer-tracing-bridge-otel`, `opentelemetry-exporter-otlp`). Trace sampling must be configured to balance performance.
* **Risks & Mitigations**: Set `management.tracing.sampling.probability` to `1.0` for dev/staging (100% traces) and `0.1` for production (10% sampling) to balance observability with performance.
