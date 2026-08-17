package com.payflow.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.payflow.AbstractIntegrationTest;
import com.payflow.dto.request.CreateUserRequest;
import com.payflow.dto.request.LoginRequest;
import com.payflow.dto.request.TransferMoneyRequest;
import com.payflow.dto.response.AuthResponse;
import com.payflow.dto.response.TransactionResponse;
import com.payflow.dto.response.UserResponse;
import com.payflow.filter.IdempotencyFilter;

class AuthenticationIT extends AbstractIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	@DisplayName("Should enforce 401 Unauthorized on protected endpoints without token and succeed with valid JWT")
	void shouldAuthenticateAndAuthorizeRequests() {
		// 1. Attempt unauthenticated call to protected endpoint -> Expect 401
		// Unauthorized
		ResponseEntity<String> unauthRes = restTemplate.getForEntity("/api/v1/users", String.class);
		assertThat(unauthRes.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

		// 2. Register User (Public Endpoint)
		String upiId = "auth.user@payflow";
		CreateUserRequest createReq = new CreateUserRequest();
		createReq.setName("Auth Test User");
		createReq.setUpiId(upiId);
		createReq.setPhoneNumber("9111223344");
		createReq.setBalance(new BigDecimal("1000.0000"));

		ResponseEntity<UserResponse> regRes = restTemplate.postForEntity("/api/v1/users", createReq,
				UserResponse.class);
		assertThat(regRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);

		// 3. Register Receiver User
		String receiverUpi = "auth.receiver@payflow";
		CreateUserRequest receiverReq = new CreateUserRequest();
		receiverReq.setName("Auth Receiver User");
		receiverReq.setUpiId(receiverUpi);
		receiverReq.setPhoneNumber("9111223355");
		receiverReq.setBalance(new BigDecimal("500.0000"));
		restTemplate.postForEntity("/api/v1/users", receiverReq, UserResponse.class);

		// 4. Login to obtain JWT Token (Public Endpoint)
		LoginRequest loginReq = new LoginRequest(upiId);
		ResponseEntity<AuthResponse> loginRes = restTemplate.postForEntity("/api/v1/auth/login", loginReq,
				AuthResponse.class);

		assertThat(loginRes.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(loginRes.getBody()).isNotNull();
		String token = loginRes.getBody().accessToken();
		assertThat(token).isNotBlank();
		assertThat(loginRes.getBody().tokenType()).isEqualTo("Bearer");

		// 5. Execute Money Transfer with Bearer Token on Protected Endpoint -> Expect
		// 201 Created
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		headers.set(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, "auth-it-key-" + UUID.randomUUID());

		TransferMoneyRequest txReq = new TransferMoneyRequest();
		txReq.setSenderUpiId(upiId);
		txReq.setReceiverUpiId(receiverUpi);
		txReq.setAmount(new BigDecimal("100.0000"));
		txReq.setNote("Authenticated Transfer Test");

		HttpEntity<TransferMoneyRequest> txEntity = new HttpEntity<>(txReq, headers);
		ResponseEntity<TransactionResponse> txRes = restTemplate.exchange("/api/v1/transactions", HttpMethod.POST,
				txEntity, TransactionResponse.class);

		assertThat(txRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(txRes.getBody()).isNotNull();
		assertThat(txRes.getBody().senderUpiId()).isEqualTo(upiId);
	}
}
