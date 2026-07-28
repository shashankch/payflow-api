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
- **Financial Arithmetic**: Never use `double` or `float` for monetary calculations. Always use `BigDecimal` with explicit scale and `RoundingMode.HALF_EVEN` (banker's rounding).
- **Error Responses**: All API errors must return standardized RFC 7807 `ProblemDetail` payloads via `@RestControllerAdvice`.
- **Logging**: Never use `System.out.println()`. Use SLF4J loggers (`log.info()`, `log.warn()`, `log.error()`).

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
- **ADRs**: Any major architectural decision (e.g., locking strategy, caching library choice) must be recorded in `docs/ADR.md`.
- **Changelog**: Every merged PR must include an entry in `CHANGELOG.md` under `[Unreleased]`.
