# Payflow API — REST API Specification

> **Document Metadata**
> - **Title**: Payflow REST API Interface & Schema Specification
> - **Author**: Payflow Engineering(`shashakchandel@gmail.com`)
> - **Status**: Approved / Living Specification
> - **Created Date**: 2026-08-01
> - **Last Updated**: 2026-08-28
> - **Authoritative Location**: [API_SPECIFICATION.md](API_SPECIFICATION.md)
> - **Related Documents**: [System Architecture](ARCHITECTURE.md) | [Security Architecture](SECURITY.md) | [Architecture Decisions (ADRs)](adr/README.md) | [Phased Roadmap](ROADMAP.md) | [Engineering Conventions](CONVENTIONS.md)

This document details the REST API endpoints, request/response models, input validation rules, and error handling behaviors for the Payflow API service.

---

## Global Conventions

- **API Base Prefix**: All endpoints are versioned and prefixed with `/api/v1`.
- **Content-Type**: All request and response bodies use `application/json`.
- **Monetary Precision**: All monetary values are encoded as standard JSON numbers with up to 4 decimal places (e.g. `100.0000`).
- **Pagination**: Default page size is 10, with a hard maximum of 100 per page (`@Min(1) @Max(100)`).
- **Authentication**: Mutation and secure history endpoints require a cryptographically signed JWT token passed via the `Authorization: Bearer <token>` header.
- **Idempotency**: All mutation write operations require a unique identifier passed in the `Idempotency-Key` header.

---

## Interactive OpenAPI & Swagger Documentation

Payflow API auto-generates live, interactive OpenAPI 3.0 documentation using **Springdoc OpenAPI 3.0.3**:
- **Swagger UI (Interactive Playground)**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI 3.0 JSON Specification**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

---

## Global Error Response Model (RFC 7807)

When an API error occurs (validation error, resource not found, conflict, etc.), the service returns a standardized error payload in compliance with RFC 7807 (Problem Details for HTTP APIs):

```json
{
  "type": "https://api.payflow.com/errors/invalid-request",
  "title": "Invalid Request Content",
  "status": 400,
  "detail": "Validation failed for request parameters.",
  "instance": "/api/v1/users",
  "timestamp": "2026-08-01T16:59:46Z",
  "errors": {
    "phoneNumber": "Phone number must be exactly 10 digits",
    "balance": "Balance must be non-negative"
  }
}
```

---

## Endpoints

### 0. User Authentication (Login)
Authenticates a registered user by UPI ID and issues a cryptographically signed JWT access token.

- **HTTP Method**: `POST`
- **Path**: `/api/v1/auth/login`
- **Authentication**: None (Public Endpoint)
- **Request Body DTO (`LoginRequest`)**:
  - `upiId`: String, required (`@NotBlank`), valid UPI format (`@Pattern`).

#### Request Example
```json
{
  "upiId": "alice@payflow"
}
```

#### Response Example (`200 OK`)
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "upiId": "alice@payflow",
  "referenceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

---

### 1. Register User
Registers a new client profile with an initial balance.

- **HTTP Method**: `POST`
- **Path**: `/api/v1/users`
- **Authentication**: None (Public Registration)
- **Request Body DTO (`CreateUserRequest`)**:
  - `name`: String, required (`@NotBlank`), max 100 chars (`@Size(max = 100)`).
  - `upiId`: String, required (`@NotBlank`), max 100 chars (`@Size(max = 100)`), valid UPI format (`@Pattern(regexp = "^[a-zA-Z0-9.\\-_]{2,64}@[a-zA-Z]{2,32}$")`).
  - `phoneNumber`: String, required (`@NotBlank`), exactly 10 digits (`@Pattern(regexp = "^\\d{10}$")`).
  - `balance`: BigDecimal, required (`@NotNull`), non-negative (`@DecimalMin("0.0")`).

#### Request Example
```json
{
  "name": "Jane Doe",
  "upiId": "janedoe@upi",
  "phoneNumber": "9876543210",
  "balance": 1000.00
}
```

#### Response Example (`201 Created`)
Headers: `Location: /api/v1/users/a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d`
```json
{
  "referenceId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
  "name": "Jane Doe",
  "upiId": "janedoe@upi",
  "phoneNumber": "9876543210",
  "balance": 1000.0000,
  "createdAt": "2026-08-01T16:00:00Z",
  "updatedAt": "2026-08-01T16:00:00Z"
}
```

#### Error Responses
- `409 Conflict`: User with the requested UPI ID already exists (`DuplicateUpiIdException`).
- `422 Unprocessable Entity`: Input validation failure, or external UPI verification rejected the UPI ID (`InvalidUpiException`, type: `https://api.payflow.com/errors/invalid-upi-id`).

---

### 2. List Users (Paginated)
Retrieves a paginated list of registered users.

- **HTTP Method**: `GET`
- **Path**: `/api/v1/users`
- **Authentication**: None
- **Query Parameters**:
  - `page`: Integer, optional. Page index (0-based, `@Min(0)`). Default: `0`.
  - `size`: Integer, optional. Page size (`@Min(1) @Max(100)`). Default: `10`.
  - `sortBy`: String, optional. Column name to sort. Default: `userId`.

#### Response Example (`200 OK`)
```json
{
  "content": [
    {
      "referenceId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
      "name": "Jane Doe",
      "upiId": "janedoe@upi",
      "phoneNumber": "9876543210",
      "balance": 1000.0000,
      "createdAt": "2026-08-01T16:00:00Z",
      "updatedAt": "2026-08-01T16:00:00Z"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

---

### 3. Retrieve User by Reference ID
Fetches a single user record by their unique UUID reference ID.

- **HTTP Method**: `GET`
- **Path**: `/api/v1/users/{id}`
- **Authentication**: `Authorization: Bearer <token>` (User can only inspect their own profile)

#### Response Example (`200 OK`)
```json
{
  "referenceId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
  "name": "Jane Doe",
  "upiId": "janedoe@upi",
  "phoneNumber": "9876543210",
  "balance": 1000.0000,
  "createdAt": "2026-08-01T16:00:00Z",
  "updatedAt": "2026-08-01T16:00:00Z"
}
```
*If not found, returns `404 Not Found`.*

---

### 4. Retrieve User by UPI ID
Fetches a single user record by their unique UPI ID.

- **HTTP Method**: `GET`
- **Path**: `/api/v1/users/upi/{upiId}`
- **Authentication**: `Authorization: Bearer <token>` (User can only inspect their own profile)

#### Response Example (`200 OK`)
```json
{
  "userId": 2,
  "name": "John Smith",
  "upiId": "johnsmith@upi",
  "phoneNumber": "9876543211",
  "balance": 50.0000,
  "version": 0,
  "createdAt": "2026-08-01T16:00:00Z",
  "updatedAt": "2026-08-01T16:00:00Z"
}
```
*If not found, returns `404 Not Found`.*

---

### 5. Retrieve User Balance Ledger History
Retrieves paginated double-entry balance ledger audit entries for a user by UUID reference ID.

- **HTTP Method**: `GET`
- **Path**: `/api/v1/users/{id}/ledger`
- **Query Parameters**:
  - `page`: Integer, optional (default `0`), min `0`.
  - `size`: Integer, optional (default `10`), min `1`, max `100`.
- **Authentication**: `Authorization: Bearer <token>` (User can only view their own ledger)

#### Response Example (`200 OK`)
```json
{
  "content": [
    {
      "ledgerId": 101,
      "userReferenceId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
      "transactionReferenceId": "f9e8d7c6-b5a4-3f2e-1d0c-9b8a7f6e5d4c",
      "entryType": "DEBIT",
      "amount": 100.0000,
      "balanceBefore": 500.0000,
      "balanceAfter": 400.0000,
      "createdAt": "2026-08-09T14:00:00Z"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true
}
```
*If user not found, returns `404 Not Found`.*

---

### 6. Create Money Transfer
Executes a peer-to-peer fund transfer request with guaranteed exactly-once idempotency and sender verification.

- **HTTP Method**: `POST`
- **Path**: `/api/v1/transactions`
- **Authentication**: `Authorization: Bearer <token>` (Authenticated JWT principal must match `senderUpiId`)
- **Headers**:
  - `Idempotency-Key`: String / UUID, **required**. Prevents duplicate debits and replays cached responses on retry.
- **Request Body DTO (`TransferMoneyRequest`)**:
  - `senderUpiId`: String, required (`@NotBlank`), max 100 chars (`@Size(max = 100)`), valid UPI format (`@Pattern(...)`).
  - `receiverUpiId`: String, required (`@NotBlank`), max 100 chars (`@Size(max = 100)`), valid UPI format (`@Pattern(...)`).
  - `amount`: BigDecimal, required (`@NotNull`), minimum `0.01` (`@DecimalMin("0.01")`), maximum `1,000,000` (`@DecimalMax("1000000.00")`).
  - `note`: String, optional, max 255 characters (`@Size(max = 255)`).

#### Request Example
```http
POST /api/v1/transactions HTTP/1.1
Host: api.payflow.com
Idempotency-Key: 9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d
Content-Type: application/json

{
  "senderUpiId": "janedoe@upi",
  "receiverUpiId": "johnsmith@upi",
  "amount": 150.00,
  "note": "Dinner bill split"
}
```

#### Response Example (`201 Created`)
Headers: `Location: /api/v1/transactions/550e8400-e29b-41d4-a716-446655440000`
```json
{
  "transactionId": 1,
  "referenceId": "550e8400-e29b-41d4-a716-446655440000",
  "senderUpiId": "janedoe@upi",
  "receiverUpiId": "johnsmith@upi",
  "amount": 150.0000,
  "status": "COMPLETED",
  "type": "TRANSFER",
  "note": "Dinner bill split",
  "createdAt": "2026-08-01T16:05:00Z"
}
```

#### Error Responses
- `400 Bad Request`: Missing `Idempotency-Key` header, or key reused with a mismatched payload.
- `401 Unauthorized`: Missing or invalid JWT bearer token.
- `403 Forbidden`: Authenticated user is not authorized to transfer from requested sender UPI.
- `409 Conflict`: A request with the same `Idempotency-Key` is currently in-flight.
- `422 Unprocessable Entity`: Validation constraint failure or insufficient sender balance.
- `429 Too Many Requests`: Per-user rate limit exceeded (maximum 10 requests per second; response includes `Retry-After: 1` header).
- `503 Service Unavailable`: Downstream dependency circuit breaker is open (`upiValidation`) or external provider failure.

---

### 7. Retrieve Transaction by Reference ID
Fetches details of a single transaction by its unique UUID reference ID.

- **HTTP Method**: `GET`
- **Path**: `/api/v1/transactions/{id}`
- **Authentication**: `Authorization: Bearer <token>` (Only sender or receiver participants can view)

#### Response Example (`200 OK`)
```json
{
  "transactionId": 1,
  "referenceId": "550e8400-e29b-41d4-a716-446655440000",
  "senderUpiId": "janedoe@upi",
  "receiverUpiId": "johnsmith@upi",
  "amount": 150.0000,
  "status": "COMPLETED",
  "type": "TRANSFER",
  "note": "Dinner bill split",
  "createdAt": "2026-08-01T16:05:00Z"
}
```
*If not found, returns `404 Not Found` (`TransactionNotFoundException`).*

---

### 8. Retrieve User Transaction History (Paginated)
Retrieves paginated transfer history (sent and received) for a given UPI ID.

- **HTTP Method**: `GET`
- **Path**: `/api/v1/transactions/user/{upiId}`
- **Query Parameters**:
  - `page` (optional, default: `0`) — Zero-based page index.
  - `size` (optional, default: `10`, max: `100`) — Page size limit.
  - `sortBy` (optional, default: `createdAt`) — Field name to sort by (descending).

#### Response Example (`200 OK`)
```json
{
  "content": [
    {
      "transactionId": 1,
      "referenceId": "550e8400-e29b-41d4-a716-446655440000",
      "senderUpiId": "janedoe@upi",
      "receiverUpiId": "johnsmith@upi",
      "amount": 150.0000,
      "status": "COMPLETED",
      "type": "TRANSFER",
      "note": "Dinner bill split",
      "createdAt": "2026-08-01T16:05:00Z"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

---

## 7. Observability & Telemetry Endpoints (Actuator)

Payflow API exposes standard Spring Boot Actuator endpoints for container health probes, Resilience4j status, and Prometheus metrics collection:

| Endpoint | Method | Auth Required | Description |
| :--- | :--- | :--- | :--- |
| `/actuator/health` | `GET` | No | Basic liveness status (`{"status": "UP"}`). Detailed status (DB, CircuitBreakers, RateLimiters) shown when authorized. |
| `/actuator/health/liveness` | `GET` | No | Kubernetes liveness probe confirming process health. |
| `/actuator/health/readiness` | `GET` | No | Kubernetes readiness probe verifying database and circuit breaker health. |
| `/actuator/info` | `GET` | No | Application build and version information. |
| `/actuator/prometheus` | `GET` | No | Prometheus format scrape output including `payflow_transfers_*`, `hikaricp_connections`, `resilience4j_circuitbreaker_*`, and `resilience4j_ratelimiter_*`. |
| `/actuator/metrics` | `GET` | No | JSON catalog of available Micrometer metric names. |

---

## Centralized Error Handling & RFC 7807 ProblemDetail

All API errors return standardized RFC 7807 `application/problem+json` response bodies enriched with timestamp and `requestId` (`X-Request-Id` correlation tracking header):

### Validation Failure (`422 Unprocessable Entity`)
```json
{
  "type": "https://api.payflow.com/errors/validation-error",
  "title": "Validation Failure",
  "status": 422,
  "detail": "Validation failed for request parameters",
  "instance": "/api/v1/users",
  "timestamp": "2026-08-03T16:25:00Z",
  "requestId": "a6b8c9d0-1234-5678-9abc-def012345678",
  "errors": {
    "phoneNumber": "Phone number must be exactly 10 digits"
  }
}
```

### Rate Limit Exceeded (`429 Too Many Requests`)
*Response includes header: `Retry-After: 1`*
```json
{
  "type": "https://api.payflow.com/errors/rate-limit-exceeded",
  "title": "Rate Limit Exceeded",
  "status": 429,
  "detail": "Too many requests. You have exceeded your rate limit of 10 requests per second. Please retry after 1 seconds.",
  "instance": "/api/v1/transactions",
  "timestamp": "2026-09-05T12:00:00Z",
  "requestId": "b7c9d1e2-3456-789a-bcde-f0123456789a"
}
```

### Service Unavailable / Circuit Breaker Open (`503 Service Unavailable`)
```json
{
  "type": "https://api.payflow.com/errors/service-unavailable",
  "title": "Service Unavailable",
  "status": 503,
  "detail": "Circuit breaker 'upiValidation' is OPEN and does not permit further calls",
  "instance": "/api/v1/transactions",
  "timestamp": "2026-09-05T12:00:01Z",
  "requestId": "c8d0e2f3-4567-89ab-cdef-0123456789ab"
}
```

---

## HTTP Status Codes Reference

| Code | Status | Trigger Condition |
| :--- | :--- | :--- |
| **200** | `OK` | Standard successful read or lookup. |
| **201** | `Created` | Successfully registered a user or created a transaction. |
| **400** | `Bad Request` | Illegal business arguments (e.g. self-transfer attempt). |
| **401** | `Unauthorized` | Missing, expired, or invalid JWT authentication token. |
| **403** | `Forbidden` | Authenticated principal is not authorized for the requested resource (sender impersonation, cross-user ledger/transaction access). |
| **404** | `Not Found` | User or transaction lookup returned no matching records (`UserNotFoundException`). |
| **409** | `Conflict` | Resource conflict (e.g. duplicate UPI ID registration, in-flight idempotency conflict, or constraint violation). |
| **422** | `Unprocessable Entity` | Jakarta validation constraint violation or insufficient account balance (`InsufficientBalanceException`). |
| **429** | `Too Many Requests` | Dynamic per-user rate limit exceeded (RFC 6585 with `Retry-After: 1` header). |
| **500** | `Internal Error` | Unexpected server error (sanitized, stack traces suppressed). |
| **503** | `Service Unavailable` | Downstream service circuit breaker is OPEN (`CallNotPermittedException`). |
