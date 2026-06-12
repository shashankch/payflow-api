# Payflow API — Backend Service

Payflow is a highly resilient, transactional payments backend built using **Java 25** and **Spring Boot 4.x**. The project is designed with a pluggable, cloud-flexible architecture optimized for local developer iteration, Kubernetes deployment, and resource-constrained environments (like AWS Free Tier).

---

## Technical Features

- **ACID Transaction Hardening**: Enforces sequential consistency under race conditions using database-level pessimistic locking (`SELECT FOR UPDATE`).
- **Distributed Locking Coordination**: Integrates **Redis Redlock** (via Redisson) to synchronize distributed triggers, preventing multi-instance concurrency collisions.
- **JPA N+1 Resolution**: Avoids Hibernate lazy loading bottlenecks through explicit **Fetch Joins** and `@EntityGraph` definitions to batch-load related entities.
- **REST API Versioning**: Implements strict URI version control (`/api/v1`) protecting endpoints from breaking database changes.
- **Gen-AI Spending Assistant**: Integrates **Spring AI** and a Generative AI LLM to automatically categorize transactions and generate spend analysis.
- **Durable Idempotency Engine**: Enforces exactly-once execution on mutation APIs via SHA-256 request payload hashing and persistent state checking.
- **Transactional Outbox Pattern**: Guarantees atomic consistency between state transitions and event streaming to Kafka without dual-write vulnerabilities.
- **Pluggable Resilience Policies**: Configured via Resilience4j for client-side rate-limiting, exponential backoff retries with jitter, and call timeouts.
- **Telemetry & Observability**: Integrated with Spring Boot Actuator, Prometheus metrics, and Logback MDC-based distributed trace correlation.

---

## Project Documentation Directory

For complete details on the architecture, evolution, and APIs, refer to the following documents:

1. 📘 **[System Architecture Guide](docs/ARCHITECTURE.md)**: Deep technical details on locking, N+1 query resolution, API versioning, AI spends insights, Spring profiles, and observability.
2. 🗓️ **[Phased Implementation Roadmap](docs/ROADMAP.md)**: Subdivided step-by-step phases (A and B) spanning database migrations, security, event streaming, and deployment.
3. 🌐 **[REST API Specification](docs/API_SPECIFICATION.md)**: Complete request and response payload schemas, versioned endpoints, validations, status codes, and RFC 7807 error formats.

---

## Spring Profiles Strategy

The application leverages profile isolation to adapt resources to the target environment dynamically:

- **`local` (Default)**: In-memory H2 database, automated hibernate schema updates, and mock environments. Excellent for standalone, dependency-free code exploration.
- **`test`**: Active during JUnit execution, utilizing **Testcontainers** to dynamically boot local Postgres/Kafka container instances for validation.
- **`prod`**: Production-grade settings. Database credentials and broker addresses are read from system environment variables. Schema evolution is strictly managed by **Flyway migrations**.
- **`prod-light` (AWS Free Tier Optimization)**: Tailored for a single constrained AWS virtual server (1 GiB RAM). Plugs out heavy Redis/Kafka containers and replaces them with lightweight in-memory fallbacks to prevent Out-Of-Memory (OOM) failures.

---

## Quick Start

### 1. Build and Run Local Tests
Requires JDK 25 and Maven. This will trigger integration tests using Testcontainers (if a Docker daemon is active) or unit tests.
```bash
mvn clean install
mvn test
```

### 2. Local Standalone Startup (Zero Dependencies)
Boots the application on port `8080` using H2 in-memory storage:
```bash
mvn spring-boot:run
```
- **H2 Console**: Available at `http://localhost:8080/h2-console`
  - *JDBC URL*: `jdbc:h2:mem:payupidb`
  - *Credentials*: `user` / `user`

### 3. Local Full-Stack Ecosystem Startup (Docker Compose)
To run a realistic local staging environment containing Postgres, Redis, Kafka KRaft, Prometheus, and Grafana:
```bash
docker-compose up --build
```
The Spring Boot instance connects to the docker network using environment variables specified in the compose file.

---

## Production Deployment Readiness

### Kubernetes Deployment
Static Kubernetes manifests are defined under the `k8s/` directory. Deploy using:
```bash
kubectl apply -f k8s/
```
- **Liveness Probe**: Routed to `/actuator/health/liveness`
- **Readiness Probe**: Routed to `/actuator/health/readiness`
- **Graceful Shutdown**: Handled via `server.shutdown=graceful` which coordinates with the container orchestration to process in-flight transactions before terminating the pod container.

### GraalVM Native Compilation
To compile the application to a lightweight, platform-native binary that starts in milliseconds and consumes only ~30-50MB of RAM:
```bash
# Compile native binary locally
mvn -Pnative native:compile

# Or build an optimized native OCI image via Buildpacks
mvn -Pnative spring-boot:build-image
```
*Note: GraalVM native compiler tools must be installed locally on the host.*
