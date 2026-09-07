# ADR-023: Redis Distributed Locking with Redisson and Fail-Safe Local Fallback

* **Date**: 2026-09-07
* **Status**: Accepted
* **Phase**: Phase 8D

## Context & Problem Statement
In distributed multi-instance deployments (such as multiple Kubernetes pods running behind an ingress load balancer), concurrent HTTP requests bearing the identical `Idempotency-Key` header can hit different application instances in the exact same millisecond.

Prior to Phase 8D, idempotency deduplication relied on database-level unique constraints on the `idempotency_records` table (`DataIntegrityViolationException`). While database constraints guarantee data consistency at the persistence layer, this approach presents several operational limitations in high-throughput financial environments:
1. **Unnecessary Connection & Thread Saturation**: Both concurrent requests acquire database connection pool slots (`HikariCP`) and begin transactions before the duplicate is caught at the `INSERT` statement.
2. **Gateway-Level Coordination Gap**: Under flash-sale bursts or aggressive client retries, competing requests execute expensive upstream validations (e.g. SHA-256 payload hashing, external gateway checks) before conflict detection occurs.
3. **Deadlock & Split-Brain Risks**: If a pod crashes mid-execution while coordinating state across microservices, locks lacking fail-safe lease times risk permanent system lockup.
4. **Developer Workflow Ergonomics**: Requiring an active Redis instance for local development and CI unit tests introduces heavy infrastructure friction and slows down feedback loops.

## Considered Options
1. **Database Unique Constraints Only (Status Quo)**: Zero additional infrastructure dependencies, but wastes database connection permits, increases write lock contention, and fails to coordinate distributed in-memory states.
2. **Spring Integration Redis Lock (`RedisLockRegistry`)**: Provides basic Redis-backed distributed locking via Spring Integration, but lacks rich locking semantics (e.g. watchdog lease renewal, robust thread-ownership checks, async locks) and pulls in unwanted Spring Integration framework bloat.
3. **Redisson Distributed Locking with Fail-Safe Local Fallback (Chosen)**:
   - Uses **Redisson** (version 4.7.0, aligned with Spring Boot 4.1.0 and Netty `4.2.15.Final`) to provide robust, Redis-backed distributed reentrant locks (`RLock`).
   - Profile-conditional architecture: `@Profile("prod")` activates `RedissonDistributedLockService` with standalone/sentinel/cluster configuration, while `@Profile("!prod")` activates `NoOpDistributedLockService` for zero-dependency local runs and tests.
   - Bounded wait time (`2 seconds`) allows a second concurrent request to wait briefly for the first in-flight transaction to finish and return the cached idempotent response.
   - Fail-safe lease duration (`10 seconds`) guarantees automatic lock release in Redis if a pod crashes, eliminating permanent deadlocks.
   - Thread-ownership verification (`isHeldByCurrentThread()`) before `unlock()` eliminates `IllegalMonitorStateException` hazards during lease timeouts.

## Decision Outcome
Chosen Option: **Redisson Distributed Locking with Fail-Safe Local Fallback (Option 3)**

### Architectural Design & Invalidation Policy

#### 1. Distributed Lock Interface & Components
- **`DistributedLockService`**: Domain service interface defining `tryLock(key, waitTime, leaseTime)`, fail-fast `tryLock(key, leaseTime)`, `unlock(key)`, and `isLocked(key)`.
- **`RedissonDistributedLockService` (`@Profile("prod")`)**: Implements Redis distributed locking via Redisson `RLock`. Restores thread interrupt state on `InterruptedException` and safely verifies `lock.isHeldByCurrentThread()` before calling `lock.unlock()`.
- **`NoOpDistributedLockService` (`@Profile("!prod")`)**: High-performance in-memory no-op implementation for `local`, `test`, and `prod-light` profiles, returning `true` for all lock attempts without external dependencies.
- **`DistributedLockConfig` (`@Profile("prod")`)**: Configures `RedissonClient` using `SingleServerConfig` with connection pool size 20, idle size 5, and timeout 3000ms.

#### 2. Idempotency Coordination Workflow
In `IdempotencyFilter`:
```
Client Request (POST /api/v1/transactions, Idempotency-Key: X)
  ├── 1. Validate Idempotency-Key header presence
  ├── 2. tryLock("payflow:lock:idemp:" + X, wait=2s, lease=10s)
  │      ├── Lock not acquired within 2s -> Return HTTP 409 Conflict ("Lock Contention")
  │      └── Lock acquired -> Proceed inside try { ... }
  ├── 3. Query idempotencyRepository.findById(X)
  │      ├── Completed -> Replay cached response immediately
  │      ├── In-flight -> Return HTTP 409 Conflict ("In Flight")
  │      └── New -> Record PROCESSING, execute filter chain, record SUCCESS/FAILED
  └── 4. finally { unlock("payflow:lock:idemp:" + X); }
```

#### 3. Namespacing & Externalized Properties
- **Key Namespace**: `payflow:lock:idemp:<idempotencyKey>`
- **Configuration (`application.yml`)**:
  ```yaml
  payflow:
    lock:
      wait-time: 2s
      lease-time: 10s
  ```

## Consequences
* **Positive**:
  - Eliminates concurrent duplicate request collisions across distributed application instances before database connection allocation.
  - Bounded 2-second wait time allows split-second duplicate requests to wait and receive the 201 cached replay response rather than immediately failing with conflict.
  - 10-second fail-safe lease time guarantees that crashed application instances never leave orphaned locks in Redis.
  - Zero-dependency local developer and unit test workflows via `NoOpDistributedLockService`.
  - Netty `4.2.15.Final` version alignment eliminates classpath conflicts with Spring Data Redis Lettuce.
* **Negative / Trade-offs**:
  - Requires a running Redis cluster/instance in production (`prod` profile).
  - Clock drift across Redis nodes in multi-master Redlock topologies must be managed via NTP synchronization (standard production requirement).
