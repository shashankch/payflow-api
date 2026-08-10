# Payflow API — REST API Specification

> **Document Metadata**
> - **Title**: Payflow REST API Interface & Schema Specification
> - **Author**: Payflow Engineering (`shashankchandel@gmail.com`)
> - **Status**: Approved / Living Specification
> - **Created Date**: 2026-08-01
> - **Last Updated**: 2026-08-05
> - **Authoritative Location**: [API_SPECIFICATION.md](API_SPECIFICATION.md)
> - **Related Documents**: [System Architecture](ARCHITECTURE.md) | [Architecture Decisions (ADRs)](ADR.md) | [Phased Roadmap](ROADMAP.md) | [Engineering Conventions](CONVENTIONS.md)

This document details the REST API endpoints, request/response models, input validation rules, and error handling behaviors for the Payflow API service.

---

## Global Conventions

- **API Base Prefix**: All endpoints are versioned and prefixed with `/api/v1`.
- **Content-Type**: All request and response bodies use `application/json`.
- **Monetary Precision**: All monetary values are encoded as standard JSON numbers with up to 4 decimal places (e.g. `100.0000`).
- **Pagination**: Default page size is 10, with a hard maximum of 100 per page (`@Min(1) @Max(100)`).
- **Authentication**: Mutation and secure history endpoints require a cryptographically signed JWT token passed via the `Authorization: Bearer <token>` header (implemented in Phase 3).
- **Idempotency**: All mutation write operations require a unique identifier passed in the `Idempotency-Key` header (implemented in Phase 4).

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
- **Authentication**: None

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
- **Authentication**: None

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
- **Authentication**: None

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
Executes a fund transfer request.

- **HTTP Method**: `POST`
- **Path**: `/api/v1/transactions`
- **Request Body DTO (`TransferMoneyRequest`)**:
  - `senderUpiId`: String, required (`@NotBlank`), max 100 chars (`@Size(max = 100)`), valid UPI format (`@Pattern(...)`).
  - `receiverUpiId`: String, required (`@NotBlank`), max 100 chars (`@Size(max = 100)`), valid UPI format (`@Pattern(...)`).
  - `amount`: BigDecimal, required (`@NotNull`), minimum `0.01` (`@DecimalMin("0.01")`), maximum `1,000,000` (`@DecimalMax("1000000.00")`).
  - `note`: String, optional, max 255 characters (`@Size(max = 255)`).

#### Request Example
```json
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

---

### 7. Retrieve Transaction by Reference ID
Fetches details of a single transaction by its unique UUID reference ID.

- **HTTP Method**: `GET`
- **Path**: `/api/v1/transactions/{id}`
- **Authentication**: None

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

## Centralized Error Handling & RFC 7807 ProblemDetail

All API errors return standardized RFC 7807 `application/problem+json` response bodies enriched with timestamp and `requestId` (`X-Request-Id` correlation tracking header):

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

---

## HTTP Status Codes Reference

| Code | Status | Trigger Condition |
| :--- | :--- | :--- |
| **200** | `OK` | Standard successful read or lookup. |
| **201** | `Created` | Successfully registered a user or created a transaction. |
| **400** | `Bad Request` | Illegal business arguments (e.g. self-transfer attempt). |
| **404** | `Not Found` | User or transaction lookup returned no matching records (`UserNotFoundException`). |
| **409** | `Conflict` | Resource conflict (e.g. duplicate UPI ID registration or database constraint violation). |
| **422** | `Unprocessable Entity` | Jakarta validation constraint violation or insufficient account balance (`InsufficientBalanceException`). |
| **500** | `Internal Error` | Unexpected server error (sanitized, stack traces suppressed). |
