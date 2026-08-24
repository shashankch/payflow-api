package com.payflow.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.payflow.AbstractIntegrationTest;
import com.payflow.dto.request.CreateUserRequest;
import com.payflow.dto.request.TransferMoneyRequest;
import com.payflow.dto.response.TransactionResponse;
import com.payflow.dto.response.UserResponse;
import com.payflow.entity.TransactionStatus;
import com.payflow.filter.IdempotencyFilter;

class OutboxIT extends AbstractIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("Should atomically persist domain event to event_publication registry on completed transfer")
	void shouldPersistEventToOutbox_whenTransferCompletes() throws InterruptedException {
		// 1. Setup Sender & Receiver
		String senderUpi = "outbox.sender@payflow";
		String receiverUpi = "outbox.receiver@payflow";

		CreateUserRequest senderReq = new CreateUserRequest();
		senderReq.setName("Outbox Sender");
		senderReq.setUpiId(senderUpi);
		senderReq.setPhoneNumber("9555111111");
		senderReq.setBalance(new BigDecimal("1000.0000"));
		restTemplate.postForEntity("/api/v1/users", senderReq, UserResponse.class);

		CreateUserRequest receiverReq = new CreateUserRequest();
		receiverReq.setName("Outbox Receiver");
		receiverReq.setUpiId(receiverUpi);
		receiverReq.setPhoneNumber("9555222222");
		receiverReq.setBalance(new BigDecimal("500.0000"));
		restTemplate.postForEntity("/api/v1/users", receiverReq, UserResponse.class);

		// 2. Execute Transfer with auth headers
		TransferMoneyRequest txReq = new TransferMoneyRequest();
		txReq.setSenderUpiId(senderUpi);
		txReq.setReceiverUpiId(receiverUpi);
		txReq.setAmount(new BigDecimal("250.0000"));
		txReq.setNote("Outbox Event Test");

		HttpHeaders headers = authHeaders(senderUpi);
		headers.set(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, "outbox-test-key-" + UUID.randomUUID());
		HttpEntity<TransferMoneyRequest> txEntity = new HttpEntity<>(txReq, headers);

		ResponseEntity<TransactionResponse> txRes = restTemplate.exchange("/api/v1/transactions", HttpMethod.POST,
				txEntity, TransactionResponse.class);
		assertThat(txRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(txRes.getBody()).isNotNull();
		assertThat(txRes.getBody().status()).isEqualTo(TransactionStatus.COMPLETED);

		UUID txReferenceId = txRes.getBody().referenceId();

		// 3. Allow async Modulith listener to complete event lifecycle
		TimeUnit.MILLISECONDS.sleep(500);

		// 4. Verify event_publication registry contains the transactional event log
		Integer eventCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM event_publication WHERE serialized_event LIKE ?", Integer.class,
				"%" + txReferenceId.toString() + "%");

		assertThat(eventCount)
				.as("Spring Modulith event publication registry must atomically persist TransferCompletedEvent")
				.isNotNull().isGreaterThanOrEqualTo(1);
	}
}
