package com.payflow.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.payflow.AbstractIntegrationTest;
import com.payflow.dto.request.CreateUserRequest;
import com.payflow.dto.request.TransferMoneyRequest;
import com.payflow.dto.response.TransactionResponse;
import com.payflow.dto.response.UserResponse;
import com.payflow.entity.IdempotencyStatus;
import com.payflow.entity.TransactionStatus;
import com.payflow.entity.User;
import com.payflow.filter.IdempotencyFilter;
import com.payflow.repository.IdempotencyRepository;
import com.payflow.repository.UserRepository;
import com.payflow.service.IdempotencyCleanupService;

class IdempotencyIT extends AbstractIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private IdempotencyRepository idempotencyRepository;

	@Autowired
	private IdempotencyCleanupService idempotencyCleanupService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("Should enforce idempotency and replay identical response without double-debiting user account")
	void shouldEnforceIdempotency_andReplayIdenticalResponseWithoutDoubleDebit() {
		// 1. Create Sender (₹1,000.00) & Receiver (₹500.00)
		String senderUpi = "idemp.sender@payflow";
		String receiverUpi = "idemp.receiver@payflow";

		CreateUserRequest senderReq = new CreateUserRequest();
		senderReq.setName("Idemp Sender");
		senderReq.setUpiId(senderUpi);
		senderReq.setPhoneNumber("9666000001");
		senderReq.setBalance(new BigDecimal("1000.0000"));
		restTemplate.postForEntity("/api/v1/users", senderReq, UserResponse.class);

		CreateUserRequest receiverReq = new CreateUserRequest();
		receiverReq.setName("Idemp Receiver");
		receiverReq.setUpiId(receiverUpi);
		receiverReq.setPhoneNumber("9666000002");
		receiverReq.setBalance(new BigDecimal("500.0000"));
		restTemplate.postForEntity("/api/v1/users", receiverReq, UserResponse.class);

		// 2. First Transfer Execution with unique Idempotency-Key
		String idempotencyKey = "client-key-" + UUID.randomUUID();
		TransferMoneyRequest txReq = new TransferMoneyRequest();
		txReq.setSenderUpiId(senderUpi);
		txReq.setReceiverUpiId(receiverUpi);
		txReq.setAmount(new BigDecimal("200.0000"));
		txReq.setNote("Idempotent Transfer Test");

		HttpHeaders headers = new HttpHeaders();
		headers.set(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, idempotencyKey);
		HttpEntity<TransferMoneyRequest> txEntity = new HttpEntity<>(txReq, headers);

		ResponseEntity<TransactionResponse> firstRes = restTemplate.postForEntity("/api/v1/transactions", txEntity,
				TransactionResponse.class);
		assertThat(firstRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(firstRes.getBody()).isNotNull();

		TransactionResponse firstTx = firstRes.getBody();
		UUID originalReferenceId = firstTx.referenceId();
		assertThat(firstTx.status()).isEqualTo(TransactionStatus.COMPLETED);
		assertThat(firstTx.amount()).isEqualByComparingTo("200.0000");

		// Verify database state after first execution
		User senderAfterFirst = userRepository.findByUpiId(senderUpi).orElseThrow();
		assertThat(senderAfterFirst.getBalance()).isEqualByComparingTo("800.0000");

		// 3. Second Transfer Attempt with EXACT SAME Idempotency-Key & Payload (Network
		// Retry Simulation)
		ResponseEntity<TransactionResponse> secondRes = restTemplate.postForEntity("/api/v1/transactions", txEntity,
				TransactionResponse.class);
		assertThat(secondRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(secondRes.getBody()).isNotNull();

		TransactionResponse secondTx = secondRes.getBody();
		assertThat(secondTx.referenceId())
				.as("Replayed idempotent response must contain original transaction reference ID")
				.isEqualTo(originalReferenceId);

		// 4. Invariant Check: Account was debited ONLY ONCE
		User senderAfterSecond = userRepository.findByUpiId(senderUpi).orElseThrow();
		assertThat(senderAfterSecond.getBalance()).as("Sender balance must remain 800.0000 with zero double-debiting")
				.isEqualByComparingTo("800.0000");

		// 5. Invariant Check: Idempotency Record in Database has SUCCESS status
		assertThat(idempotencyRepository.findById(idempotencyKey)).isPresent().hasValueSatisfying(record -> {
			assertThat(record.getStatus()).isEqualTo(IdempotencyStatus.SUCCESS);
			assertThat(record.getResponseCode()).isEqualTo(201);
			assertThat(record.getResponseBody()).contains(originalReferenceId.toString());
		});
	}

	@Test
	@DisplayName("Should purge expired idempotency records older than configured TTL")
	void shouldPurgeExpiredIdempotencyRecords_viaCleanupService() {
		String expiredKey = "expired-key-" + UUID.randomUUID();
		Instant expiredTime = Instant.now().minus(25, ChronoUnit.HOURS);

		jdbcTemplate.update(
				"INSERT INTO idempotency_registry (idempotency_key, request_hash, status, created_at, updated_at) "
						+ "VALUES (?, ?, ?, ?, ?)",
				expiredKey, "dummy_hash_12345", IdempotencyStatus.SUCCESS.name(), java.sql.Timestamp.from(expiredTime),
				java.sql.Timestamp.from(expiredTime));

		assertThat(idempotencyRepository.existsById(expiredKey)).isTrue();

		int purgedCount = idempotencyCleanupService.purgeExpiredRecords();
		assertThat(purgedCount).isGreaterThanOrEqualTo(1);

		assertThat(idempotencyRepository.existsById(expiredKey)).isFalse();
	}
}
