# ADR-028: Multi-Stage Containerization and Full-Stack Docker Compose Orchestration

* **Date**: 2026-09-29
* **Status**: Accepted
* **Phase**: Phase 11A

## Context & Problem Statement

As Payflow API matured into an enterprise-grade payment engine featuring PostgreSQL 17, Redis 7, Apache Kafka (KRaft), Ollama Gen-AI, and Prometheus/Grafana observability, developers and deployment pipelines required an immutable, reproducible, and secure containerization strategy. 

Traditional single-stage Docker images carry build dependencies (JDK, Maven, build caches, source trees) into production runtime environments, yielding excessively bloated image footprints (>800 MB), slow network transfer latencies, and an unnecessarily vast security attack surface. Furthermore, running production application containers as the privileged `root` user violates defense-in-depth principles (CIS Docker Benchmark, NIST SP 800-190). Finally, local development and staging environments require automated orchestration of all dependent backing services with automated healthchecks, volume persistence, and telemetry scrapers.

## Considered Options

### Option A: Single-Stage JDK Base Image Running as Root

- **Pros**: Minimal Dockerfile complexity.
- **Cons**: Severe security vulnerability (container breakout risks as root); image sizes exceed 850 MB; includes unnecessary compiler, build tools, and transient package managers in production.

### Option B: JLink-Based Custom Minimal Runtime via Alpine Linux

- **Pros**: Extremely tiny container footprint (~80 MB).
- **Cons**: High build complexity; glibc/musl compatibility issues with modern dynamic libraries; non-trivial debugging and maintenance overhead with Java 25 previews.

### Option C: Multi-Stage Build with Eclipse Temurin 25 JRE, Non-Root User, and Full-Stack Compose (Chosen)

- **Pros**:
  - Clear separation of concerns: Stage 1 (`eclipse-temurin:25-jdk`) builds the executable fat JAR using layer caching; Stage 2 (`eclipse-temurin:25-jre`) provides a lean, hardened runtime (~250 MB).
  - Enforces least privilege: Executes under dedicated unprivileged non-root system user (`payflow:10001`).
  - Active Healthchecks: Implements Docker `HEALTHCHECK` targeting Spring Boot Actuator readiness endpoint (`/actuator/health/readiness`).
  - Generational ZGC & JVM Memory Ergonomics: Sets `-XX:+UseZGC -XX:+ZGenerational -XX:MaxRAMPercentage=75.0` to respect container cgroup memory limits.
  - Comprehensive Docker Compose: Orchestrates PostgreSQL 17, Redis 7, Apache Kafka KRaft, Ollama, Prometheus, and Grafana with volume persistence and health dependency ordering (`condition: service_healthy`).
- **Cons**: Slightly longer initial Docker build due to multi-stage isolation, fully mitigated by BuildKit layer caching and `.dockerignore`.

## Decision Outcome

We adopted **Option C**. Key implementation highlights include:

1. **Multi-Stage `Dockerfile`**:
   - **Builder Stage**: `eclipse-temurin:25-jdk` copies Maven descriptors and resolves dependencies before compiling application sources to optimize layer reuse.
   - **Runtime Stage**: `eclipse-temurin:25-jre` provides a secure, minimal execution environment.
   - **Hardened User**: Created dedicated user/group `payflow:10001` with no login shell; all application files are owned by `payflow`.
   - **Liveness & Readiness**: Integrated `curl` for container health probes (`HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3`).
   - **Optimized Runtime Flags**: Configured Generational ZGC (`-XX:+UseZGC -XX:+ZGenerational`) and container memory auto-sizing (`-XX:MaxRAMPercentage=75.0`).

2. **Hardened `.dockerignore`**:
   - Strictly excludes `target/`, `.git/`, `.github/`, IDE metadata, local scripts, and documentation, ensuring minimal build context transmission to the Docker daemon.

3. **Production-Ready `docker-compose.yml`**:
   - **PostgreSQL 17**: `postgres:17-alpine` with `pg_isready` healthcheck and named volume `postgres_data`.
   - **Redis 7**: `redis:7-alpine` with `redis-cli ping` healthcheck and named volume `redis_data`.
   - **Apache Kafka (KRaft)**: `apache/kafka:latest` running in controller/broker combined KRaft mode (no Zookeeper required) with persistent volume `kafka_data`.
   - **Ollama**: `ollama/ollama:latest` for local Gen-AI spend categorization models with persistent volume `ollama_data`.
   - **Prometheus & Grafana**: Automatically provisions Prometheus scrape jobs for `app:8080/actuator/prometheus` and auto-provisions Grafana dashboards with datasource connections.

## Consequences

### Positive

- **Reduced Attack Surface**: No compiler, package cache, or root privileges exist in the runtime container.
- **Predictable Garbage Collection**: Generational ZGC provides sub-millisecond GC pauses across container memory allocations.
- **One-Command Environment Launch**: Developers can execute `docker compose up -d` to bring up the entire distributed banking infrastructure with health-checked dependency startup.

### Trade-offs & Mitigations

- **Base Image Updates**: Minor base image patches must be audited periodically. Automated GitHub Dependabot and CI workflows verify base image freshness.
