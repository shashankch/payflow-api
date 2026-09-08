# Payflow API — Engineering Conventions & Standards

This document outlines the coding standards, repository conventions, Git workflow rules, testing guidelines, and PR expectations for the Payflow API project.

---

## 1. Code Style & Formatting

- **Java Version**: Java 25.
- **Formatter**: Google Java Format, enforced via the `spotless-maven-plugin`.
- **Linter**: Checkstyle, enforced via `maven-checkstyle-plugin`.
- **Indentation**: 4 spaces for Java, 2 spaces for YAML/JSON, 4 spaces for XML.
- **Imports**: Group imports logically; avoid wildcard imports (`import java.util.*`) except in test files where approved.
- **Naming Conventions**:
  - `PascalCase` for classes, interfaces, enums, annotations.
  - `camelCase` for variables, method names, fields, parameter names.
  - `UPPER_SNAKE_CASE` for `public static final` constants and enum values.

---

## 2. Architecture & Design Standards

- **Constructor Injection**: Always use constructor injection for Spring beans. Field injection via `@Autowired` is prohibited.
- **DTO Separation**: Database entities (`@Entity`) must never be exposed directly via REST controllers. Use DTOs for request input and Java `record`s for API response models.
- **Immutability**: Prefer immutable data structures. Response DTOs should use Java `record`s where possible.
- **Financial Arithmetic & Currency**: All monetary amounts are strictly denominated in Indian Rupees (**INR**, symbol: **₹**). Never use `double` or `float` for monetary calculations. Always use `BigDecimal` with explicit scale (`precision = 19, scale = 4`) and `RoundingMode.HALF_EVEN` (banker's rounding).
- **Error Responses**: All API errors must return standardized RFC 7807 `ProblemDetail` payloads via `@RestControllerAdvice`.
- **Logging**: Never use `System.out.println()`. Use SLF4J loggers named `LOG` (`private static final Logger LOG = LoggerFactory.getLogger(...)`) with structured MDC enrichment (`LOG.info()`, `LOG.warn()`, `LOG.error()`).

---

## 3. Git Workflow & Commit Messages

- **Branch Naming**: `feat/phase-<num>-<short-description>`, `fix/<short-description>`, `docs/<short-description>`.
- **Commit Message Format**: Follow Conventional Commits:
  ```text
  <type>(<scope>): <short summary in imperative mood>

  [optional body explaining rationale]
  ```
  - **Types**: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `ci`.
  - **Example**: `feat(domain): replace Double balance with BigDecimal in User entity`
- **PR Size**: Keep PRs small and scoped to a single sub-phase (target: <500 lines of code changed).

---

## 4. Testing Conventions

- **Unit Tests**:
  - Located in `src/test/java/...`.
  - Class naming: `[ClassName]Test.java` (e.g., `TransactionServiceTest.java`).
  - Use Mockito for dependencies and MockMVC for controller slice tests (`@WebMvcTest`).
  - Must run quickly without requiring Docker or external services.
- **Integration Tests**:
  - Located in `src/test/java/.../integration/`.
  - Class naming: `[Feature]IT.java` or `[ClassName]IntegrationTest.java`.
  - Use Testcontainers to spin up real PostgreSQL/Kafka containers.
- **Test Method Naming**: Use descriptive names reflecting intent:
  - `should[ExpectedBehavior]_when[StateUnderTest]()`
  - Example: `shouldRejectTransfer_whenSenderHasInsufficientBalance()`

---

## 5. Documentation Standards

- **Parity**: Every feature implemented in code must be documented in `README.md`, `docs/ARCHITECTURE.md`, and `docs/API_SPECIFICATION.md`.
- **ADRs**: Any major architectural decision (e.g., locking strategy, caching library choice) must be recorded as an individual numbered ADR under `docs/adr/`.
- **Changelog**: Every merged PR must include an entry in `CHANGELOG.md` under `[Unreleased]`.
- **Design Document Structure**: All architectural design documents in `docs/` must incorporate standard design document sections:
  - **Metadata Header**: Short title, author, creation date, status (Draft/Approved), and authoritative relative link.
  - **Executive Summary & Background**: High-level problem statement, business motivation, and non-obvious domain context.
  - **Goals & Non-Goals**: Explicit product/technical goals alongside explicit out-of-scope boundaries to prevent scope creep.
  - **SLOs & Constraints**: Quantifiable availability, latency (P95/P99), throughput, and data retention metrics.
  - **Threat Modeling & Security**: Threat matrix with architectural mitigations and compliance rules.
  - **Alternatives Considered ("Cost of Getting It Wrong")**: Systematic evaluation of rejected options, trade-offs, and failure consequences.
  - **Open & Resolved Issues**: Tracking resolved decisions and unresolved design questions.
