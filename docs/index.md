# ₹ Payflow API

### Enterprise Transaction & Double-Entry Payment Ledger Engine

<p align="left">
  <a href="https://github.com/shashankch/payflow-api/actions/workflows/ci.yml"><img src="https://img.shields.io/badge/CI-Passing-brightgreen?logo=githubactions&logoColor=white&style=flat-square" alt="CI Build"></a>
  <a href="https://shashankch.github.io/payflow-api/coverage/"><img src="https://img.shields.io/badge/Coverage-90%25%20Line%20%7C%2073%25%20Branch-brightgreen?style=flat-square" alt="JaCoCo Coverage"></a>
  <a href="https://dev.java/"><img src="https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white&style=flat-square" alt="Java 25"></a>
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring%20Boot-4.1.0-6DB33F?logo=springboot&logoColor=white&style=flat-square" alt="Spring Boot 4.1.0"></a>
  <a href="https://github.com/shashankch/payflow-api/blob/main/Dockerfile"><img src="https://img.shields.io/badge/Docker-Multi--stage%20Temurin%2025-2496ED?logo=docker&logoColor=white&style=flat-square" alt="Docker"></a>
  <a href="https://github.com/shashankch/payflow-api/tree/main/k8s"><img src="https://img.shields.io/badge/Kubernetes-HPA%20%26%20RollingUpdate-326CE5?logo=kubernetes&logoColor=white&style=flat-square" alt="Kubernetes"></a>
  <a href="ARCHITECTURE.md#15-testing-strategy-rigor-concurrency--unit-verification"><img src="https://img.shields.io/badge/Tests-191%20Passing-brightgreen?logo=junit5&logoColor=white&style=flat-square" alt="JUnit 5 Tests"></a>
  <a href="ARCHITECTURE.md"><img src="https://img.shields.io/badge/Architecture-Modular%20Monolith-6366f1?style=flat-square" alt="Modular Monolith"></a>
  <a href="LICENSE.md"><img src="https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square" alt="License: MIT"></a>
</p>

A high-concurrency peer-to-peer payment backend built with **Java 25** and **Spring Boot 4.1.0**.  
**Guaranteed zero double-spending • Deterministic row locking • Distributed Redisson locks • Immutable balance ledger • Transactional Outbox & Kafka streaming**

---

## 🚀 Key Architectural Pillars

| Architectural Pillar | Core Guarantee & Engineering Mechanics |
| :--- | :--- |
| **🔒 Concurrency Safety** | Row-level pessimistic write locking (`SELECT ... FOR UPDATE`) paired with deterministic alphabetical lock ordering by UPI ID, eliminating race conditions and deadlocks during concurrent account debits and credits. |
| **📜 Double-Entry Ledger** | Atomic paired `DEBIT` and `CREDIT` records with strict base-10 `BigDecimal` arithmetic precision denominated in Indian Rupees (`INR`, symbol: `₹`, scale = 4) and Banker's Rounding (`HALF_EVEN`), preserving an immutable financial audit trail. |
| **🔁 Durable Idempotency** | Mandatory `Idempotency-Key` headers (validated 255-char regex boundary) backed by raw SHA-256 payload hashing to prevent tampering, coupled with Redisson distributed locking (`payflow:lock:idemp:{key}`) to coordinate mutations across multi-instance clusters. |
| **🛡️ Resilience & Fault Tolerance** | Dynamic per-user rate limiting (10 req/s, RFC 6585 `Retry-After: 1`), Resilience4j circuit breaking on external banking rails, bounded timeouts, and automatic memory eviction of inactive limiter buckets. |
| **⚡ Transactional Outbox** | Spring Modulith Event Publication Registry atomically persisting domain events (`TransferCompletedEvent`) within the database transaction, bridging to Apache Kafka without dual-write inconsistency, with automated background retention cleanup (`OutboxCleanupService`). |
| **🔐 Zero-Trust Security** | Stateless HMAC-SHA256 JWT tokens with fail-fast production secret validation, strict principal-bound sender verification, role-based access control (`ROLE_ADMIN` on user enumeration), clickjacking defense (`sameOrigin`), and RFC 9457 ProblemDetail error responses. |
| **📊 Enterprise Observability** | Native Elastic Common Schema (ECS) JSON structured logging, MDC trace correlation (`requestId`, `traceId`, `spanId`), Prometheus metrics, and profile-conditional Redis distributed caching with targeted cache eviction and Caffeine local fallback. |
| **🤖 Gen-AI Spend Insights** | Spring AI 2.0.1 integration providing automated expenditure classification and contextual budgeting tips with structured JSON output, guarded by Resilience4j circuit breakers and deterministic keyword heuristic fallback. |
| **🚀 Virtual Threads & Concurrency** | Java 25 Project Loom Virtual Threads enabled globally (`spring.threads.virtual.enabled: true`), with bounded HikariCP connection pool configurations and a low-memory `prod-light` profile designed for <= 1 GiB single-node production environments. |
| **🐳 Cloud-Native Orchestration & Quality Gates** | Hardened multi-stage Docker build (`eclipse-temurin:25-jre`, unprivileged `payflow:10001` user), full-stack Docker Compose (PostgreSQL 17, Redis 7, Kafka KRaft, Ollama, Prometheus, Grafana), Kubernetes HPA/PDB topology with zero-downtime graceful shutdown, SpotBugs static analysis, and automated JaCoCo coverage enforcement (90% Line / 73% Branch). |

---

## 🏗️ System Architecture Overview

![System Architecture Overview](assets/diagrams/system-overview.svg)

<details>
<summary>📐 View Declarative D2 Diagram Source</summary>

```d2
direction: down

client: Client / Mobile App {
  shape: rectangle
  icon: "docs/assets/icons/client.svg"
}

ingress: Ingress / API Gateway {
  shape: rectangle
  icon: "docs/assets/icons/gateway.svg"
}

security: Spring Security (JWT Filter) {
  shape: rectangle
  icon: "docs/assets/icons/shield.svg"
}

idemp: Idempotency Filter (SHA-256) {
  shape: rectangle
  icon: "docs/assets/icons/lock.svg"
}

controller: TransactionController {
  shape: rectangle
  icon: "docs/assets/icons/spring.svg"
}

service: TransactionService {
  shape: rectangle
  icon: "docs/assets/icons/java.svg"
}

client -> ingress: "HTTP POST (Idempotency-Key)"
ingress -> security: "TLS 1.3"
security -> idemp: "Bearer JWT"
idemp -> controller: "Validated Request"
controller -> service: "sendMoney()"

core_storage: Core Concurrency & Storage {
  postgres: PostgreSQL 17 {
    shape: cylinder
    icon: "docs/assets/icons/postgresql.svg"
  }
  redis: Redis 7 (Redlock & Cache) {
    shape: cylinder
    icon: "docs/assets/icons/redis.svg"
  }
}

async_events: Asynchronous Messaging & Events {
  outbox: Transactional Outbox {
    shape: cylinder
    icon: "docs/assets/icons/queue.svg"
  }
  kafka: Apache Kafka (KRaft) {
    shape: queue
    icon: "docs/assets/icons/kafka.svg"
  }
  outbox -> kafka: "spring-modulith-events-kafka"
}

ai_resilience: AI & Resilience {
  ai: Ollama Gen-AI {
    shape: rectangle
    icon: "docs/assets/icons/ai.svg"
  }
  r4j: Resilience4j {
    shape: rectangle
    icon: "docs/assets/icons/shield.svg"
  }
}

service -> core_storage.postgres: "Row Lock & Ledger Entries"
service -> core_storage.redis: "Redlock & Cache-Aside"
service -> async_events.outbox: "Atomically Persist Event"
service -> ai_resilience.ai: "Spend Insights"
service -> ai_resilience.r4j: "Rate Limiting Guard"
```
</details>

---

## 📚 Project Documentation Hub

| Document | Description |
| :--- | :--- |
| 📊 **[Code Coverage & Quality Gates](coverage.md)** | Automated CI/CD quality gates, bundle-level line/branch thresholds, and SpotBugs audit |
| 🚀 **[Interactive JaCoCo Report](https://shashankch.github.io/payflow-api/coverage-report/)** | Live interactive code coverage drilldown (90% Line, 73% Branch) generated per-build |
| 📘 **[System Architecture](ARCHITECTURE.md)** | Deep-dive concurrency models, pessimistic locking mechanics, test pyramid |
| 🛡️ **[Security Architecture & Threat Model](ARCHITECTURE.md#18-security-architecture-and-threat-model)** | Zero-Trust filter chain, STRIDE threat model, IAM policy matrix, financial concurrency controls |
| 🌐 **[REST API Specification](API_SPECIFICATION.md)** | Complete REST endpoint contracts, schemas, RFC 9457 ProblemDetail payloads |
| 🗓️ **[Phased Roadmap](ROADMAP.md)** | Full 12-phase technical expansion blueprint and milestone statuses |
| 📋 **[Engineering Conventions](CONVENTIONS.md)** | Java 25 standards, Spotless/Checkstyle rules, testing guidelines |
| 📜 **[Architecture Decisions (ADRs)](adr/README.md)** | Master index of modular architectural decision records (ADR-001 through ADR-030) |
| 📝 **[Changelog](CHANGELOG.md)** | Version-by-version implementation notes |
| 🔒 **[Security Policy](SECURITY.md)** | Open-source vulnerability reporting guidelines and project security posture |
| 🤝 **[Contributing Guide](CONTRIBUTING.md)** | Development environment setup and pull request requirements |

---

## ⚡ Quick Start

=== "Local Development"
    ```bash
    # Start server with active 'local' profile (H2 in-memory, port 8080)
    mvn spring-boot:run
    ```
    - **Swagger UI Interactive Docs**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
    - **OpenAPI 3.0 JSON Specification**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
    - **H2 Console**: [http://localhost:8080/h2-console](http://localhost:8080/h2-console) (`jdbc:h2:mem:payupidb`, Credentials: `user` / `user`)

=== "Docker Compose"
    ```bash
    # Spin up complete distributed stack (Postgres, Redis, Kafka, Ollama, Prometheus, Grafana)
    docker compose up -d --build
    ```
    - **Payflow API**: [http://localhost:8080](http://localhost:8080)
    - **Prometheus**: [http://localhost:9090](http://localhost:9090)
    - **Grafana**: [http://localhost:3000](http://localhost:3000) (Credentials: `admin` / `admin`)
    - **Ollama AI**: [http://localhost:11434](http://localhost:11434)

=== "Kubernetes (HPA & Topology)"
    ```bash
    # Deploy declarative manifests to Kubernetes cluster
    kubectl apply -f k8s/
    ```
    - ConfigMap, Secrets, Deployment with rolling updates
    - Horizontal Pod Autoscaler (HPA) min 2, max 10
    - Pod Disruption Budget (PDB) minAvailable: 1

=== "Quality Verification Pipeline"
    ```bash
    # Execute full verification pipeline: Spotless, Checkstyle, SpotBugs, Tests & JaCoCo gates
    mvn clean verify -DskipITs
    ```
    - **Spotless**: Code formatting verification
    - **Checkstyle**: Enterprise static code linting
    - **SpotBugs**: Bytecode bug pattern analysis
    - **JaCoCo**: Quality gates requiring >= 80% line and >= 70% branch coverage (90% / 73% achieved across 191 tests)
