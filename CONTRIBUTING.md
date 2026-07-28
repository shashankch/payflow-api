# Contributing to Payflow API

Thank you for participating in the evolution of the Payflow API payment backend!

This guide outlines our development workflow, pull request expectations, quality gates, and repository standards.

---

## 1. Development Workflow

1. **Fork & Clone**: Clone the repository locally.
2. **Branch Naming**: Create a topic branch following our convention:
   - `feat/phase-<num>-<short-description>` for new features
   - `fix/<short-description>` for bug fixes
   - `docs/<short-description>` for documentation updates
3. **Local Build & Quality Check**:
   Before opening a PR, ensure code formatting, static analysis, and tests pass:
   ```bash
   mvn spotless:apply
   mvn clean verify
   ```

---

## 2. Code Quality Gates & Automated Checks

Every pull request triggers the **GitHub Actions CI pipeline**, which enforces strict quality gates:

- **Formatting Check (`Spotless`)**: Validates code layout against Eclipse/Java format rules (`mvn spotless:check`).
- **Static Analysis (`Checkstyle`)**: Ensures compliance with naming, import, and structural standards (`mvn checkstyle:check`).
- **Compilation & Test Suite**: Verifies clean compilation on Java 25 and executes all unit and integration tests.

> **Note**: PRs with failing CI checks, unformatted code, or lint violations will not be merged.

---

## 3. Conventional Commit Guidelines

We enforce the [Conventional Commits](https://www.conventionalcommits.org/) specification for clean git history:

- **Format**: `<type>(<scope>): <short summary in imperative mood>`
- **Allowed Types**:
  - `feat`: A new feature or endpoint implementation
  - `fix`: A bug fix in existing logic
  - `docs`: Documentation changes (`README`, architecture guide, specs)
  - `style`: Formatting changes that do not affect code logic
  - `refactor`: Code reorganization without functional changes
  - `test`: Adding or refactoring tests
  - `chore`: Updating dependencies, build scripts, or configuration
- **Examples**:
  - `feat(domain): replace Double balance with BigDecimal in User entity`
  - `fix(locking): sort user UPI IDs alphabetically to prevent deadlock`

---

## 4. Pull Request Requirements

- **Scoped Changes**: Keep PRs small and aligned with a single sub-phase (target: <500 net lines of code).
- **Documentation Parity**: If changing API contracts, business logic, or configuration parameters, update `README.md`, `docs/ARCHITECTURE.md`, and `docs/API_SPECIFICATION.md` within the same PR.
- **Changelog Entry**: Add a concise entry under `[Unreleased]` in `CHANGELOG.md`.

---

## 5. Recommended Repository Branch Protection Rules

For repository maintainers, configure GitHub branch protection for `main`:

- **Require a pull request before merging**.
- **Require status checks to pass before merging**: Select `Build, Quality Audit & Test`.
- **Require linear history** (squash or rebase merging preferred).
