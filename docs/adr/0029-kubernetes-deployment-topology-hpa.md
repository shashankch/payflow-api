# ADR-029: Cloud-Native Kubernetes Deployment Topology and Horizontal Pod Autoscaling

* **Date**: 2026-09-29
* **Status**: Accepted
* **Phase**: Phase 11B

## Context & Problem Statement

Production payment systems must guarantee high availability (99.99%), zero-downtime rolling updates, automated resilience against pod/node failures, and dynamic elasticity to handle unpredictable payment traffic spikes (e.g., peak salary credit days or festive shopping sales). 

Deploying bare-metal or single-instance application containers risks service disruptions during deployments or traffic surges. The operational architecture required a standard cloud-native Kubernetes deployment topology incorporating:
1. Declarative separation between configuration (`ConfigMap`), credentials (`Secret`), and workload lifecycle (`Deployment`).
2. Coordination of graceful shutdown to prevent dropping in-flight HTTP transactions during rolling redeployments or scale-down events.
3. Proactive health probe integration distinguishing between pod startup, deadlocks (liveness), and traffic readiness (readiness).
4. Dynamic resource elasticity scaling pods horizontally based on CPU and memory utilization thresholds, backed by disruption budgets protecting minimum quorum.

## Considered Options

### Option A: Static Replica Deployment without Probes or HPA
- **Pros**: Simplest manifest configuration.
- **Cons**: Prone to downtime during rolling updates (traffic routed to non-ready pods); unable to adapt to transaction bursts; manual operator intervention required for scaling.

### Option B: Cloud-Managed Serverless Container Platform (e.g., AWS Fargate / Google Cloud Run)
- **Pros**: Fully managed infrastructure; automatic scaling to zero.
- **Cons**: High cold-start latencies for JVM applications; vendor lock-in; inflexible networking constraints with stateful backing databases and Apache Kafka clusters.

### Option C: Declarative Kubernetes Topology with Rolling Updates, Graceful PreStop Hooks, HPA, and PDB (Chosen)
- **Pros**:
  - Cloud-agnostic and portable across any certified Kubernetes cluster (EKS, GKE, AKS, OpenShift, or local Minikube/Kind).
  - High availability via zero-downtime `RollingUpdate` (`maxSurge: 1`, `maxUnavailable: 0`).
  - Graceful Shutdown Coordination: Pre-stop hook (`sleep 10`) coordinates with `server.shutdown: graceful` and `spring.lifecycle.timeout-per-shutdown-phase: 30s` to drain active requests before SIGTERM terminates the process.
  - Granular Actuator Probes: Directly maps Kubernetes `livenessProbe` to `/actuator/health/liveness` and `readinessProbe` to `/actuator/health/readiness`.
  - Automated Horizontal Pod Autoscaling (`HPA`): Dynamically scales replicas between 2 and 10 based on 75% CPU and 80% memory utilization.
  - Quorum Preservation: `PodDisruptionBudget` (`minAvailable: 1`) guarantees continuous service availability during node drains and cluster upgrades.
- **Cons**: Requires standard Kubernetes cluster infrastructure and metrics server.

## Decision Outcome

We adopted **Option C**. The architecture includes:

1. **Manifest Suite (`k8s/`)**:
   - `k8s/configmap.yaml`: Houses non-sensitive environment configuration (`SPRING_PROFILES_ACTIVE`, service endpoints for PostgreSQL, Redis, Kafka, and Ollama).
   - `k8s/secret.yaml`: Provides base64/stringData secrets separation for database credentials and cryptographic JWT keys.
   - `k8s/deployment.yaml`: Defines the core workload with 2 initial replicas, non-root security context (`runAsUser: 10001`), resource requests (`cpu: 250m, memory: 512Mi`) and limits (`cpu: 1000m, memory: 1024Mi`).
   - `k8s/service.yaml`: Exposes an internal `ClusterIP` on port 8080.
   - `k8s/hpa.yaml`: Configures `HorizontalPodAutoscaler` (min 2, max 10 pods) targeting 75% CPU utilization and 80% memory utilization with rapid scale-up and 300s scale-down stabilization windows.
   - `k8s/pdb.yaml`: Establishes `PodDisruptionBudget` ensuring `minAvailable: 1`.

2. **Graceful Shutdown Lifecycle Coordination**:
   - Kubernetes deregistration is asynchronous: kube-proxy and Ingress controllers require several seconds to prune terminating pod IPs from endpoint slices.
   - We configured a `lifecycle.preStop` hook executing `sleep 10`. During this delay, the pod stops receiving new inbound connections while existing connections continue servicing.
   - Once the pre-stop hook completes, Kubernetes delivers `SIGTERM`. Spring Boot’s graceful shutdown (`server.shutdown: graceful` and `spring.lifecycle.timeout-per-shutdown-phase: 30s`) stops the embedded Tomcat connector and allows up to 30 seconds for in-flight database transactions and message publications to conclude cleanly before JVM termination.

## Consequences

### Positive
- **Zero-Downtime Deployments**: New pods achieve readiness verification prior to old pod retirement.
- **Traffic Burst Protection**: HPA automatically expands application capacity under sudden high-volume UPI transaction surges.
- **Transaction Safety**: Graceful shutdown coordination eliminates dropped connections and incomplete transactions during pod cycling.

### Trade-offs & Mitigations
- **Deployment Duration**: The 10s pre-stop sleep intentionally extends rolling update completion time by a few seconds; this trade-off is essential for 100% transaction integrity.
