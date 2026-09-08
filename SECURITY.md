# Security Policy — Payflow API

> **Project Status**: Payflow API is an open-source educational and reference architecture project licensed under the [MIT License](LICENSE). The project is currently under active development and is not deployed to production environments.

---

## Architectural Security Model

Payflow API implements defense-in-depth security principles for financial ledger integrity, including Zero-Trust request validation, STRIDE threat mitigations, deterministic row locking, immutable double-entry balance ledgers, and SHA-256 payload verification.

For full architectural specifications, see [Security Architecture & Threat Model](docs/ARCHITECTURE.md#18-security-architecture-and-threat-model).

---

## Reporting Vulnerabilities & Security Issues

Because this repository is in active development:

- **Bug & Vulnerability Reports**: If you discover a security flaw, vulnerability, or architectural weakness, please open an issue on [GitHub Issues](https://github.com/shashankch/payflow-api/issues) labeled `security`.
- **Private Advisory**: If you prefer private disclosure, you may submit a [GitHub Private Vulnerability Report](https://github.com/shashankch/payflow-api/security/advisories/new).
- **Details to Include**: Provide a concise summary of the issue, affected component/endpoint, and reproduction steps or proof-of-concept.
- **Contributions**: Pull requests hardening configurations or patching security vulnerabilities are warmly welcomed.
