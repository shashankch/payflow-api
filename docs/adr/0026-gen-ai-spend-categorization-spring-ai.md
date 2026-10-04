# ADR-026: Gen-AI Spend Categorization with Spring AI and Circuit Breaker Fallback

* **Date**: 2026-09-28
* **Status**: Accepted
* **Phase**: Phase 10A

## Context & Problem Statement

Modern peer-to-peer (P2P) payment and digital wallet platforms provide end-users with contextual financial intelligence, automated transaction categorization, and actionable budgeting recommendations. As transaction volume scales, users require visibility into their spending patterns across common categories (Food & Dining, Shopping, Travel & Transit, Utilities, Entertainment, Healthcare, Investments, and Transfers).

Integrating Large Language Models (LLMs) to analyze unstructured transaction metadata presents key architectural challenges in a mission-critical financial backend:

1. **Third-Party Latency & Availability**: External LLM inference APIs introduce variable latency (typically 500ms to 3000ms) and are susceptible to transient network timeouts, HTTP 429 rate-limiting, and downstream outages. Core payment engine availability must never depend on third-party AI availability.
2. **Schema Integrity & Hallucination**: Financial responses require deterministic structures with strict type safety, validated numeric confidence scores, and clean separation between raw LLM outputs and domain entities.
3. **Operational Cost & Configuration**: Self-hosted or cloud deployments may not wish to incur LLM API token expenses by default, requiring feature flag toggling and graceful zero-dependency fallback.

## Considered Options

### Option A: Ad-Hoc REST Client Calls to OpenAI Endpoints

- **Pros**: Minimal new dependency overhead; standard `RestClient` or `WebClient` usage.
- **Cons**: High manual prompt engineering boilerplate; brittle JSON string deserialization; tight vendor lock-in to OpenAI API schemas; lacking standardized model switching (e.g., Anthropic, Ollama, Azure OpenAI).

### Option B: Python/FastAPI Sidecar Microservice

- **Pros**: Native Python AI ecosystem (LangChain, LlamaIndex).
- **Cons**: Violates Payflow's Spring Modulith architectural boundary (ADR-0008); adds network hop overhead, serialization latency, multi-runtime operational complexity, and separate deployment lifecycles.

### Option C: Spring AI 2.0.1 GA Fluent ChatClient with Resilience4j Circuit Breaker and Deterministic Heuristic Fallback (Chosen)

- **Pros**:
  - Native Spring ecosystem integration using `spring-ai-starter-model-openai` (Spring AI 2.0.1 GA).
  - Fluent `ChatClient` API with automatic structured entity conversion (`.call().entity(SpendInsightResponse.class)`).
  - Built-in Resilience4j `@CircuitBreaker` fault tolerance with sub-second failover to an in-process keyword heuristic fallback (`ruleBasedFallback`).
  - Strict feature flagging via `payflow.ai.enabled` preventing unauthorized external egress when disabled.
  - Zero disruption to core transaction processing or ledger integrity.
- **Cons**: Adds Spring AI dependency footprint to classpath; requires maintaining fallback heuristic rule definitions.

## Decision Outcome

We adopted **Option C**. The architectural implementation consists of:

1. **Spring AI 2.0.1 Abstraction**:
   - Integrated `org.springframework.ai:spring-ai-bom` (2.0.1) and `spring-ai-starter-model-openai`.
   - Configured `ChatClient` bean in `AiConfig` conditional on model builder availability.
   - Structured JSON response extraction directly into the immutable Java 25 record `SpendInsightResponse`.

2. **Resilience4j Fault Tolerance & Fallback**:
   - Decorated `LlmInsightClient.generate()` with `@CircuitBreaker(name = "aiCircuitBreaker", fallbackMethod = "ruleBasedFallback")`.
   - Configured a dedicated `aiCircuitBreaker` instance with sliding window size of 10, failure rate threshold of 50%, slow call duration threshold of 3s, and sliding window call evaluation.
   - Implemented `ruleBasedFallback(Transaction, Throwable)` providing deterministic keyword-matching heuristic categorization across 8 major consumer expenditure categories:
     - `FOOD_AND_DINING` (Swiggy, Zomato, Starbucks, Blinkit, Instamart, groceries, dining)
     - `SHOPPING` (Amazon, Flipkart, Myntra, retail, mall)
     - `TRANSPORTATION` (Uber, Ola, metro, fuel, petrol, transit)
     - `UTILITIES` (Electricity, water, gas, broadband, recharge, power)
     - `ENTERTAINMENT` (Netflix, Spotify, Prime, cinema, theatre, game)
     - `HEALTHCARE` (Hospital, pharmacy, Apollo, clinic, doctor)
     - `INVESTMENTS` (Zerodha, Groww, mutual fund, stock, SIP, gold)
     - `TRANSFER` (Default generic peer-to-peer remittance)

3. **Controller & Domain Security**:
   - Exposed `POST /api/v1/transactions/{id}/insights`.
   - Enforced principal ownership validation via `TransactionService.getTransactionByReferenceId` ensuring users can only request insights for transactions where they are the authenticated sender or receiver.
   - Protected endpoint behind `payflow.ai.enabled` feature flag. When disabled, the service throws `FeatureDisabledException` returning an RFC 9457 HTTP 503 `ProblemDetail`.

## Consequences

### Positive

- **Guaranteed High Availability**: If the AI model times out or encounters rate limits, requests instantly fallback to rule-based insights without user-facing HTTP 500 errors.
- **Type-Safe Contract**: Responses conform strictly to `SpendInsightResponse` schema with transparent metadata indicator (`source = "AI_MODEL"` vs `"RULE_BASED_FALLBACK"`).
- **Zero Cost When Disabled**: Default configuration `payflow.ai.enabled: false` ensures zero unexpected external API calls during standard test suites or air-gapped environments.
- **Vendor Agility**: Spring AI's `ChatClient` decouples application logic from specific model providers, allowing seamless migration to local Ollama or cloud models in future phases.

### Trade-offs & Mitigations

- **Fallback Accuracy**: Keyword heuristics produce lower confidence scores (0.70 to 0.85) compared to generative reasoning (0.90 to 0.98). Mitigated by explicitly returning `confidenceScore` and `source` in the response payload for consumer transparency.
