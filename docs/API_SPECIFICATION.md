# Payflow API — REST API Specification

This document details the REST API endpoints, request/response models, input validation rules, and error handling behaviors for the Payflow API service.

---

## Global Conventions

- **API Base Prefix**: All endpoints are versioned and prefixed with `/api/v1`.
- **Content-Type**: All request and response bodies must use `application/json`.
- **Authentication**: Mutation and secure history endpoints require a cryptographically signed JWT token passed via the `Authorization: Bearer <token>` header.
- **Idempotency**: All mutation write operations (specifically `POST /api/v1/transactions`) require a unique identifier passed in the `Idempotency-Key` header.

---

## Global Error Response Model (RFC 7807)

When an API error occurs (validation error, resource not found, conflict, etc.), the service returns a standardized error payload in compliance with RFC 7807 (Problem Details for HTTP APIs):

```json
{
  "type": "https://api.payflow.com/errors/invalid-transfer-amount",
  "title": "Invalid Request Content",
  "status": 422,
  "detail": "The transaction amount must be a positive number greater than zero.",
  "instance": "/api/v1/transactions",
  "timestamp": "2026-06-12T16:59:46Z",
  "errors": {
    "amount": "must be greater than 0.0"
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
- **Request Body Validation**:
  - `name`: String, required, cannot be blank. Max length 100 characters.
  - `phoneNumber`: String, required, must match standard 10-digit format (regex: `^\d{10}$`).
  - `upiId`: String, required, must follow UPI address format (regex: `^[\w.-]+@[\w.-]+$`).
  - `balance`: Double, optional. Defaults to `0.0`. Must be positive.

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
```json
{
  "userId": 1,
  "name": "Jane Doe",
  "upiId": "janedoe@upi",
  "phoneNumber": "9876543210",
  "balance": 1000.00
}
```

---

### 2. List Users (Paginated)
Retrieves a paginated list of registered users.

- **HTTP Method**: `GET`
- **Path**: `/api/v1/users`
- **Authentication**: None
- **Query Parameters**:
  - `page`: Integer, optional. Page index (0-based). Default: `0`.
  - `size`: Integer, optional. Page size. Default: `10`.
  - `sortBy`: String, optional. Column name to sort. Default: `name`.
  - `minBalance`: Double, optional. Filter users with balance strictly greater than this amount.

#### Response Example (`200 OK`)
```json
{
  "content": [
    {
      "userId": 1,
      "name": "Jane Doe",
      "upiId": "janedoe@upi",
      "phoneNumber": "9876543210",
      "balance": 1000.00
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 10,
    "sort": {
      "empty": false,
      "sorted": true,
      "unsorted": false
    }
  },
  "totalPages": 1,
  "totalElements": 1
}
```

---

### 3. Retrieve User by ID
Fetches a single user record by its primary database key.

- **HTTP Method**: `GET`
- **Path**: `/api/v1/users/{id}`
- **Authentication**: None

#### Response Example (`200 OK`)
```json
{
  "userId": 1,
  "name": "Jane Doe",
  "upiId": "janedoe@upi",
  "phoneNumber": "9876543210",
  "balance": 1000.00
}
```
*If not found, returns `404 Not Found` with a standard RFC 7807 error payload.*

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
  "balance": 50.00
}
```
*If not found, returns `404 Not Found` with a standard RFC 7807 error payload.*

---

### 5. Create Money Transfer
Executes an atomic transfer of funds between two users.

- **HTTP Method**: `POST`
- **Path**: `/api/v1/transactions`
- **Required Headers**:
  - `Authorization: Bearer <JWT_Token>`
  - `Idempotency-Key: <UUID_v4_String>`
- **Request Body Validation**:
  - `senderUpiId`: String, required, must match the sender's authenticated UPI context.
  - `receiverUpiId`: String, required, cannot equal `senderUpiId`.
  - `amount`: Double, required, must be strictly greater than `0.0`.
  - `note`: String, optional, max 255 characters.

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
```json
{
  "transactionId": 105,
  "senderUpiId": "janedoe@upi",
  "receiverUpiId": "johnsmith@upi",
  "amount": 150.00,
  "note": "Dinner bill split"
}
```

---

### 6. Retrieve Transaction Status Check
Queries a single transaction status by its ID.

- **HTTP Method**: `GET`
- **Path**: `/api/v1/transactions/{id}`
- **Authentication**: None (or authenticated User context)

#### Response Example (`200 OK`)
```json
{
  "transactionId": 105,
  "senderUpiId": "janedoe@upi",
  "receiverUpiId": "johnsmith@upi",
  "amount": 150.00,
  "note": "Dinner bill split",
  "timestamp": "2026-06-12T17:26:13Z"
}
```

---

### 7. Transaction Refund / Reversal
Performs a database-level refund of a previous transaction.

- **HTTP Method**: `POST`
- **Path**: `/api/v1/transactions/{id}/refund`
- **Required Headers**:
  - `Authorization: Bearer <JWT_Token>`
  - `Idempotency-Key: <UUID_v4_String>`
- **Request Body**:
  - `reason`: String, optional, max 255 characters.

#### Request Example
```json
{
  "reason": "Duplicate charge on dining bill"
}
```

#### Response Example (`201 Created`)
```json
{
  "refundTransactionId": 201,
  "originalTransactionId": 105,
  "senderUpiId": "johnsmith@upi",
  "receiverUpiId": "janedoe@upi",
  "amount": 150.00,
  "note": "Refund: Duplicate charge on dining bill",
  "timestamp": "2026-06-12T17:42:21Z"
}
```

---

### 8. User Transaction History (Paginated)
Retrieves a paginated list of transaction history (debits and credits) associated with a user, optimized to prevent JPA N+1 select calls.

- **HTTP Method**: `GET`
- **Path**: `/api/v1/users/{id}/transactions`
- **Authentication**: `Bearer JWT Token` (User must be accessing their own history)
- **Query Parameters**:
  - `page`: Integer, optional. Default: `0`.
  - `size`: Integer, optional. Default: `20`.
  - `type`: String, optional. Filter transactions by type (`DEBIT`, `CREDIT`).

#### Response Example (`200 OK`)
```json
{
  "content": [
    {
      "transactionId": 105,
      "type": "DEBIT",
      "senderUpiId": "janedoe@upi",
      "receiverUpiId": "johnsmith@upi",
      "amount": 150.00,
      "note": "Dinner bill split",
      "timestamp": "2026-06-12T17:26:13Z"
    }
  ],
  "totalPages": 1,
  "totalElements": 1
}
```

---

### 9. AI Transaction Spend Insights
Retrieves an automated categorization and budget warning insight for a transaction using Spring AI and a Generative AI LLM.

- **HTTP Method**: `POST`
- **Path**: `/api/v1/transactions/{id}/insights`
- **Authentication**: `Bearer JWT Token` (User must own the transaction)

#### Response Example (`200 OK`)
```json
{
  "transactionId": 105,
  "category": "Food & Dining",
  "sentiment": "NEUTRAL",
  "insight": "This dining transaction represents 12% of your monthly food budget. You have spent $350 on dining this month, which is on track to stay within your $500 monthly limit."
}
```

---

## HTTP Status Codes Reference

| Code | Status | Trigger Condition |
| :--- | :--- | :--- |
| **200** | `OK` | Standard successful read, lookup, or insights generation. |
| **201** | `Created` | Successfully registered a user, executed a transaction, or processed a refund. |
| **400** | `Bad Request` | Malformed JSON payloads, schema mismatch, or reusing an `Idempotency-Key` with a different request payload. |
| **401** | `Unauthorized` | Missing or invalid cryptographically signed JWT token in `Authorization` header. |
| **403** | `Forbidden` | Authenticated actor attempting to perform a transaction or retrieve records of a different user account. |
| **404** | `Not Found` | User or transaction lookup returned no records. |
| **409** | `Conflict` | Submitting a request with an `Idempotency-Key` that is currently in a `PROCESSING` state. |
| **422** | `Unprocessable Entity` | Schema fields fail validation checks (e.g. invalid phone number syntax, negative transfer amounts, or insufficient account balance). |
| **429** | `Too Many Requests` | Rate limiter threshold exceeded. Client is temporarily rate-limited. |
| **500** | `Internal Error` | Database connection lost, Kafka dispatch failure, or AI Model API network error. |
