# ADR-027: Java 25 Virtual Threads and Bounded HikariCP Connection Pool Optimization

* **Date**: 2026-09-28
* **Status**: Accepted
* **Phase**: Phase 10B

## Context & Problem Statement

Payflow API runs on Java 25 LTS with Spring Boot 4.1. Traditional enterprise Java applications allocate an operating system (OS) platform thread for each concurrent HTTP request (thread-per-request model). Each platform thread consumes approximately 1 MB of stack memory and incurs CPU kernel scheduling overhead during context switching. When threads block on I/O operations—such as PostgreSQL database transactions, Redis lookups, or external REST API calls—platform threads remain pinned and idle, limiting concurrent request capacity.

However, migrating to Project Loom Virtual Threads (`java.lang.VirtualThread`) introduces specific operational considerations in a transactional payment system:
1. **Virtual Threads Do Not Scale Database Connections**: While millions of virtual threads can be spawned with negligible memory overhead (~1 KB per virtual thread), downstream relational databases (PostgreSQL) cannot sustain unbounded concurrent connections. Unchecked connection requests cause HikariCP pool starvation, excessive thread checkout latency, and database CPU context-switching thrashing.
2. **Resource Constraints in Small-Footprint Deployments**: Many cloud edge nodes, branch gateways, and resource-constrained environments operate with strict memory limits (e.g., 1 GiB RAM). Running full distributed infrastructure (distributed Redis clusters and multi-broker Apache Kafka clusters) in such environments introduces unacceptable memory pressure and operational cost.

## Considered Options

### Option A: Retain Traditional OS Platform Threads with Tomcat Pool Tuning
- **Pros**: Established operational baseline; well-understood thread dump analysis.
- **Cons**: High memory consumption under concurrent load; limited concurrency ceiling (~200 concurrent requests before thread exhaustion); inefficient resource utilization during blocking I/O.

### Option B: Migrate to Asynchronous Reactive Architecture (Spring WebFlux / R2DBC)
- **Pros**: Non-blocking I/O with small, fixed thread pools.
- **Cons**: Requires complete architectural rewrite; breaks compatibility with Spring Data JPA, Hibernate ORM, and Spring Security thread-local contexts; significantly elevates code complexity and cognitive overhead for standard banking domain logic.

### Option C: Enable Virtual Threads Globally with Bounded HikariCP Sizing and a Dedicated `prod-light` Profile (Chosen)
- **Pros**:
  - Leverages Java 25 LTS Project Loom virtual threads natively via `spring.threads.virtual.enabled: true`.
  - Retains idiomatic synchronous, blocking programming model with full JPA/Hibernate and Spring Security compatibility.
  - Bounded HikariCP connection pool configurations protect PostgreSQL from saturation.
  - Introduces a dedicated `prod-light` profile designed for 1 GiB single-node production instances using high-performance local Caffeine caching and in-process Spring Modulith event publication.
- **Cons**: Requires disciplined monitoring of HikariCP pool metrics and virtual thread carrier thread pinning.

## Decision Outcome

We adopted **Option C**. The architectural enhancements comprise:

1. **Global Virtual Thread Enablement**:
   - Enabled `spring.threads.virtual.enabled: true` in `application.yml`.
   - Embedded Tomcat dispatches all incoming HTTP requests to virtual threads, allowing high concurrency while I/O waits release the underlying carrier platform threads.

2. **Bounded HikariCP Connection Pool Tuning**:
   - Configured bounded connection limits to align with the formula $PoolSize = 2 \times CoreCount + DiskSpindleCount$:
     - **`prod` Profile**: `maximum-pool-size: 20`, `minimum-idle: 10`, `connection-timeout: 30000ms`, `idle-timeout: 300000ms` (5 minutes), and `max-lifetime: 1800000ms` (30 minutes).
     - **`prod-light` Profile**: `maximum-pool-size: 5`, `minimum-idle: 2`, `connection-timeout: 30000ms`, `idle-timeout: 300000ms`, and `max-lifetime: 1800000ms`.
   - Explicit `idle-timeout` and `max-lifetime` prevent stale connection accumulation and periodic firewall severance.

3. **Dedicated `prod-light` Deployment Profile**:
   - Added `application-prod-light.yml` for lean single-node deployments requiring $\le$ 1 GiB total memory footprint:
     - **Cache Layer**: High-performance local Caffeine caching (`spring.cache.type: caffeine`) with 600s TTL and 500-entry capacity, completely eliminating Redis infrastructure requirements.
     - **Event Processing**: In-process synchronous Spring Modulith event publication (`spring.modulith.events.externalization.enabled: false`) with local event listeners (`@Profile({"local", "test", "prod-light"})`), eliminating Apache Kafka dependencies.
     - **Security Invariant**: Enforced enterprise-grade JWT secret validation in `JwtTokenProvider` matching `prod` profile security requirements (rejects default test secret, requires $\ge$ 256-bit entropy).

## Consequences

### Positive
- **High Concurrency Throughput**: Virtual threads decouple concurrency limits from OS thread counts, servicing thousands of concurrent requests with near-zero thread overhead.
- **PostgreSQL Stability**: Bounded HikariCP pools prevent connection exhaustion and protect database CPU utilization.
- **Low Footprint Agility**: The `prod-light` profile enables enterprise-grade deployments on 1 GiB single-node instances with under 512 MB JVM heap requirements.
- **Full Backward Compatibility**: Business logic, JPA repositories, and declarative transaction management remain 100% standard synchronous Java.

### Trade-offs & Mitigations
- **Carrier Thread Pinning**: Synchronized blocks holding I/O operations can pin carrier threads. Mitigated in Java 25 where runtime pinning has been significantly minimized, and all internal Payflow services employ `ReentrantLock` or non-synchronized primitives.
