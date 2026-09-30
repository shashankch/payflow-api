# Stage 1: Build stage with OpenJDK 25 and Maven
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /workspace

# Copy build definition files first to leverage Docker layer caching
COPY pom.xml ./
COPY spotbugs-exclude.xml ./
COPY checkstyle.xml ./

# Copy source code and build production jar
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    apt-get update && apt-get install -y --no-install-recommends maven && \
    mvn clean package -DskipTests -B

# Stage 2: Minimal hardened runtime stage
FROM eclipse-temurin:25-jre AS runtime

# Install curl for container health check
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# Create dedicated non-root application user and group (UID/GID 10001)
RUN groupadd --system --gid 10001 payflow && \
    useradd --system --uid 10001 --gid payflow --home /app --shell /bin/sh payflow

WORKDIR /app

# Copy the built jar artifact from builder stage
COPY --from=builder --chown=payflow:payflow /workspace/target/payflow-api-*.jar app.jar

# Switch to unprivileged non-root user
USER 10001:10001

EXPOSE 8080

# Configure production-ready ZGC Generational flags and container memory limits
ENV JAVA_OPTS="-XX:+UseZGC -XX:+ZGenerational -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Healthcheck targeting Spring Boot Actuator readiness probe
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
