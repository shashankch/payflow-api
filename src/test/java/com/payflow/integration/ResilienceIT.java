package com.payflow.integration;

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

import static org.assertj.core.api.Assertions.assertThat;

class ResilienceIT extends AbstractIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	@DisplayName("Should enforce per-user rate limit returning 429 and export Resilience4j metrics")
	void shouldEnforcePerUserRateLimitingAndExportMetrics() {
		// 1. Register User Alice and User Bob
		String aliceUpi = "resilience.alice@payflow";
		CreateUserRequest aliceReq = new CreateUserRequest();
		aliceReq.setName("Resilience Alice");
		aliceReq.setUpiId(aliceUpi);
		aliceReq.setPhoneNumber("9444111222");
		aliceReq.setBalance(new BigDecimal("5000.0000"));
		restTemplate.postForEntity("/api/v1/users", aliceReq, UserResponse.class);

		String bobUpi = "resilience.bob@payflow";
		CreateUserRequest bobReq = new CreateUserRequest();
		bobReq.setName("Resilience Bob");
		bobReq.setUpiId(bobUpi);
		bobReq.setPhoneNumber("9444111223");
		bobReq.setBalance(new BigDecimal("5000.0000"));
		restTemplate.postForEntity("/api/v1/users", bobReq, UserResponse.class);

		// 2. Login to acquire JWT for Alice and Bob
		LoginRequest aliceLogin = new LoginRequest(aliceUpi);
		ResponseEntity<AuthResponse> aliceAuth = restTemplate.postForEntity("/api/v1/auth/login", aliceLogin,
				AuthResponse.class);
		assertThat(aliceAuth.getBody()).isNotNull();
		String aliceToken = aliceAuth.getBody().accessToken();

		LoginRequest bobLogin = new LoginRequest(bobUpi);
		ResponseEntity<AuthResponse> bobAuth = restTemplate.postForEntity("/api/v1/auth/login", bobLogin,
				AuthResponse.class);
		assertThat(bobAuth.getBody()).isNotNull();
		String bobToken = bobAuth.getBody().accessToken();

		// 3. Alice rapidly sends transfers up to and beyond the 10 req/s limit
		boolean rateLimitHit = false;
		ResponseEntity<String> rateLimitResponse = null;

		for (int i = 0; i < 15; i++) {
			TransferMoneyRequest txReq = new TransferMoneyRequest();
			txReq.setSenderUpiId(aliceUpi);
			txReq.setReceiverUpiId(bobUpi);
			txReq.setAmount(new BigDecimal("10.0000"));
			txReq.setNote("Rate limit test transfer #" + i);

			HttpHeaders headers = new HttpHeaders();
			headers.setBearerAuth(aliceToken);
			headers.set(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString());
			HttpEntity<TransferMoneyRequest> entity = new HttpEntity<>(txReq, headers);

			ResponseEntity<String> response = restTemplate.exchange("/api/v1/transactions", HttpMethod.POST, entity,
					String.class);

			if (response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
				rateLimitHit = true;
				rateLimitResponse = response;
				break;
			}
		}

		// 4. Verify Alice was throttled with RFC 6585 & RFC 7807 429
		assertThat(rateLimitHit).isTrue();
		assertThat(rateLimitResponse).isNotNull();
		assertThat(rateLimitResponse.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(rateLimitResponse.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
		assertThat(rateLimitResponse.getBody()).contains("Rate Limit Exceeded");
		assertThat(rateLimitResponse.getBody()).contains("rate-limit-exceeded");

		// 5. Verify Per-User Isolation: Bob should NOT be throttled even though Alice
		// is
		TransferMoneyRequest bobTx = new TransferMoneyRequest();
		bobTx.setSenderUpiId(bobUpi);
		bobTx.setReceiverUpiId(aliceUpi);
		bobTx.setAmount(new BigDecimal("20.0000"));
		bobTx.setNote("Bob independent transfer");

		HttpHeaders bobHeaders = new HttpHeaders();
		bobHeaders.setBearerAuth(bobToken);
		bobHeaders.set(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString());
		HttpEntity<TransferMoneyRequest> bobEntity = new HttpEntity<>(bobTx, bobHeaders);

		ResponseEntity<TransactionResponse> bobResponse = restTemplate.exchange("/api/v1/transactions", HttpMethod.POST,
				bobEntity, TransactionResponse.class);

		assertThat(bobResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(bobResponse.getBody()).isNotNull();
		assertThat(bobResponse.getBody().status()).isEqualTo("COMPLETED");

		// 6. Verify /actuator/prometheus exports Resilience4j metrics
		ResponseEntity<String> prometheusRes = restTemplate.getForEntity("/actuator/prometheus", String.class);
		assertThat(prometheusRes.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(prometheusRes.getBody()).contains("resilience4j");

		// 7. Verify /actuator/health is UP
		ResponseEntity<String> healthRes = restTemplate.getForEntity("/actuator/health", String.class);
		assertThat(healthRes.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(healthRes.getBody()).contains("UP");
	}
}
