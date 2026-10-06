# 📊 Automated Code Coverage & Quality Metrics

[![Coverage](https://img.shields.io/badge/Coverage-90%25%20Line%20%7C%2073%25%20Branch-brightgreen?style=flat-square)](https://shashankch.github.io/payflow-api/coverage-report/)
[![Quality Gate](https://img.shields.io/badge/SpotBugs-Passed%20(0%20Bugs)-brightgreen?style=flat-square)](https://github.com/shashankch/payflow-api/actions/workflows/ci.yml)
[![Tests](https://img.shields.io/badge/Tests-191%20Passed%20%7C%200%20Failed-brightgreen?style=flat-square)](https://github.com/shashankch/payflow-api/actions/workflows/ci.yml)

The Payflow API continuous integration pipeline strictly enforces automated bytecode static analysis via **SpotBugs** and bundle-level code coverage thresholds via **JaCoCo** (`jacoco-maven-plugin:0.8.15`).

---

## 🚀 Live Interactive JaCoCo Report

The full, interactive class-by-class and package-by-package JaCoCo coverage report is automatically generated on every build and published alongside the documentation portal.

<p align="center" style="margin: 32px 0;">
  <a id="jacoco-direct-link" href="https://shashankch.github.io/payflow-api/coverage-report/" class="md-button md-button--primary" style="font-weight: 600; padding: 12px 28px; font-size: 0.95rem;">
    📊 Launch Interactive JaCoCo Code Coverage Report &rarr;
  </a>
</p>

All granular package coverage breakdowns, class drilldowns, line-by-line highlights, branch evaluations, and bytecode audit metrics are maintained dynamically in the live report above.
