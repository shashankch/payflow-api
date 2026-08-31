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

class ObservabilityIT extends AbstractIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	@DisplayName("Should expose health, prometheus metrics, and record custom transfer business metrics")
	void shouldExposeHealthAndPrometheusMetrics() {
		// 1. Verify /actuator/health is UP
		ResponseEntity<String> healthRes = restTemplate.getForEntity("/actuator/health", String.class);
		assertThat(healthRes.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(healthRes.getBody()).contains("UP");

		// 2. Register Sender & Receiver
		String senderUpi = "obs.sender@payflow";
		CreateUserRequest senderReq = new CreateUserRequest();
		senderReq.setName("Obs Sender");
		senderReq.setUpiId(senderUpi);
		senderReq.setPhoneNumber("9333444555");
		senderReq.setBalance(new BigDecimal("2000.0000"));
		restTemplate.postForEntity("/api/v1/users", senderReq, UserResponse.class);

		String receiverUpi = "obs.receiver@payflow";
		CreateUserRequest receiverReq = new CreateUserRequest();
		receiverReq.setName("Obs Receiver");
		receiverReq.setUpiId(receiverUpi);
		receiverReq.setPhoneNumber("9333444556");
		receiverReq.setBalance(new BigDecimal("500.0000"));
		restTemplate.postForEntity("/api/v1/users", receiverReq, UserResponse.class);

		// 3. Login to acquire JWT
		LoginRequest loginReq = new LoginRequest(senderUpi);
		ResponseEntity<AuthResponse> loginRes = restTemplate.postForEntity("/api/v1/auth/login", loginReq,
				AuthResponse.class);
		assertThat(loginRes.getStatusCode()).isEqualTo(HttpStatus.OK);
		String token = loginRes.getBody().accessToken();

		// 4. Perform Transfer with X-Request-Id header
		String customRequestId = UUID.randomUUID().toString();
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		headers.set(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString());
		headers.set("X-Request-Id", customRequestId);

		TransferMoneyRequest transferReq = new TransferMoneyRequest();
		transferReq.setSenderUpiId(senderUpi);
		transferReq.setReceiverUpiId(receiverUpi);
		transferReq.setAmount(new BigDecimal("250.0000"));
		transferReq.setNote("Observability test transfer");

		HttpEntity<TransferMoneyRequest> entity = new HttpEntity<>(transferReq, headers);
		ResponseEntity<TransactionResponse> txRes = restTemplate.exchange("/api/v1/transactions", HttpMethod.POST,
				entity, TransactionResponse.class);

		assertThat(txRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(txRes.getHeaders().getFirst("X-Request-Id")).isEqualTo(customRequestId);

		// 5. Scrape /actuator/prometheus
		ResponseEntity<String> prometheusRes = restTemplate.getForEntity("/actuator/prometheus", String.class);
		assertThat(prometheusRes.getStatusCode()).isEqualTo(HttpStatus.OK);
		String metricsBody = prometheusRes.getBody();
		assertThat(metricsBody).isNotNull();

		// Verify custom business metrics and Hikari connection metrics
		assertThat(metricsBody).contains("payflow_transfers_total");
		assertThat(metricsBody).contains("payflow_transfers_amount");
		assertThat(metricsBody).contains("payflow_transfers_duration");
		assertThat(metricsBody).contains("hikaricp_connections");
	}
}
