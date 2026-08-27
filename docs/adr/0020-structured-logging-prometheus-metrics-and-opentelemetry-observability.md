# ADR-020: Structured Logging, Prometheus Metrics & OpenTelemetry Observability Architecture

* **Date**: 2026-08-27
* **Status**: Accepted
* **Phase**: Phase 8A

## Context & Problem Statement
In a high-throughput financial backend, raw unstructured plain text logs and unmonitored execution paths prevent real-time incident diagnosis, SLA alerting, and end-to-end request tracing. Operating Payflow across micro-benchmarks or distributed cloud nodes requires unified telemetry covering:
1. Machine-readable structured logs with contextual correlation IDs (`requestId`, `traceId`, `spanId`, `userId`, `http.status`, `http.latency_ms`).
2. High-precision system and business metrics exportable to Prometheus for monitoring payment success/failure rates, transfer latency percentiles, and database connection pool saturation.
3. Vendor-agnostic distributed tracing compliant with W3C Trace Context standards.

## Considered Options
1. **Vendor-Specific Telemetry Agents**: Commercial proprietary agents (e.g. Datadog, New Relic) embedded in the JVM. Causes vendor lock-in, increases runtime overhead, and conflicts with container portability.
2. **Ad-hoc Custom Logging & Metric Counters**: Manual `System.currentTimeMillis()` logs and in-memory static counters. Lacks standardized scraping interfaces, histogram percentiles, and distributed trace context propagation.
3. **Unified Spring Observation API + Micrometer Prometheus & OpenTelemetry Bridge (Chosen)**:
   - Built-in Spring Boot 3.4+ / 4.x structured logging (`logging.structured.format.console: ecs` in production).
   - Micrometer Prometheus Registry exposing `/actuator/prometheus` with custom business meters (`payflow.transfers.total`, `payflow.transfers.amount`, `payflow.transfers.duration`) and HikariCP connection metrics.
   - Micrometer Tracing with OpenTelemetry bridge (`micrometer-tracing-bridge-otel`) and `@Observed` method-level instrumentation for automatic W3C `traceparent` propagation and span creation.

## Decision Outcome
Chosen Option: **Unified Spring Observation API with Micrometer Prometheus & OpenTelemetry Bridge (Option 3)**

### Rationale
* **Three Pillars of Observability**: Unifies logs, metrics, and traces into a single cohesive telemetry model via Spring's `Observation` / `ObservationRegistry`.
* **Zero Vendor Lock-in**: Standard W3C Trace Context headers and OpenTelemetry protocols (OTLP) enable seamless integration with any backend (Prometheus, Grafana Loki, Tempo, Jaeger, OpenSearch).
* **Business SLA Visibility**: Custom distribution summaries and timers compute p50, p95, and p99 percentiles for transfer amounts and execution latencies directly on Prometheus scrapes.
* **HikariCP Monitoring**: Actuator tracks database pool utilization (`PayflowHikariPool`), preventing connection starvation under concurrency spikes.
* **Environment-Adaptive Logging**: Production uses structured Elastic Common Schema (ECS) JSON for automated log aggregators; local and test profiles maintain ANSI colored human-readable logs with MDC correlation tags.

## Consequences
* **Positive**: Complete observability with zero vendor lock-in, automated MDC correlation across all HTTP requests, and out-of-the-box Prometheus alerting integration.
* **Negative / Trade-offs**: Requires minor memory overhead for metric meter registrations and percentile histogram bucketing.
