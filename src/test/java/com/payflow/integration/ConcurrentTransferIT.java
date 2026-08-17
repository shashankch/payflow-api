package com.payflow.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
import com.payflow.dto.request.TransferMoneyRequest;
import com.payflow.dto.response.TransactionResponse;
import com.payflow.dto.response.UserResponse;
import com.payflow.entity.User;
import com.payflow.filter.IdempotencyFilter;
import com.payflow.repository.UserRepository;

class ConcurrentTransferIT extends AbstractIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private UserRepository userRepository;

	@Test
	@DisplayName("10 concurrent threads attempt ₹100 transfers from ₹150 balance: exactly 1 succeeds, 9 fail with 422, balance never goes negative")
	void shouldPreventDoubleSpending_underHighConcurrency() throws InterruptedException {
		// 1. Create Sender with ₹150.00 initial balance
		String senderUpi = "sender.race@payflow";
		CreateUserRequest senderReq = new CreateUserRequest();
		senderReq.setName("Race Sender");
		senderReq.setUpiId(senderUpi);
		senderReq.setPhoneNumber("9876000000");
		senderReq.setBalance(new BigDecimal("150.0000"));
		restTemplate.postForEntity("/api/v1/users", senderReq, UserResponse.class);

		// 2. Create 10 distinct Receivers with ₹0.00 initial balance
		int threadCount = 10;
		String[] receiverUpis = new String[threadCount];
		for (int i = 0; i < threadCount; i++) {
			receiverUpis[i] = "receiver" + i + ".race@payflow";
			CreateUserRequest receiverReq = new CreateUserRequest();
			receiverReq.setName("Receiver " + i);
			receiverReq.setUpiId(receiverUpis[i]);
			receiverReq.setPhoneNumber("987600000" + (i + 1));
			receiverReq.setBalance(BigDecimal.ZERO);
			restTemplate.postForEntity("/api/v1/users", receiverReq, UserResponse.class);
		}

		// 3. Set up synchronized thread execution using CountDownLatch
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CountDownLatch readyLatch = new CountDownLatch(threadCount);
		CountDownLatch startLatch = new CountDownLatch(1);
		CountDownLatch finishLatch = new CountDownLatch(threadCount);

		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failureCount = new AtomicInteger(0);

		for (int i = 0; i < threadCount; i++) {
			final String receiverUpi = receiverUpis[i];
			executor.submit(() -> {
				readyLatch.countDown();
				try {
					startLatch.await(); // Wait for all threads to be ready
					TransferMoneyRequest txReq = new TransferMoneyRequest();
					txReq.setSenderUpiId(senderUpi);
					txReq.setReceiverUpiId(receiverUpi);
					txReq.setAmount(new BigDecimal("100.0000"));
					txReq.setNote("Concurrent race attempt");

					HttpHeaders headers = authHeaders(senderUpi);
					headers.set(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString());
					HttpEntity<TransferMoneyRequest> entity = new HttpEntity<>(txReq, headers);

					ResponseEntity<TransactionResponse> response = restTemplate.exchange("/api/v1/transactions",
							HttpMethod.POST, entity, TransactionResponse.class);

					if (response.getStatusCode() == HttpStatus.CREATED) {
						successCount.incrementAndGet();
					} else if (response.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY) {
						failureCount.incrementAndGet();
					}
				} catch (Exception e) {
					failureCount.incrementAndGet();
				} finally {
					finishLatch.countDown();
				}
			});
		}

		// Release all threads simultaneously
		readyLatch.await(5, TimeUnit.SECONDS);
		startLatch.countDown();
		finishLatch.await(10, TimeUnit.SECONDS);
		executor.shutdown();

		// 4. Assert Invariants
		assertThat(successCount.get()).as("Exactly one ₹100 transfer should succeed from ₹150 balance").isEqualTo(1);

		assertThat(failureCount.get()).as("Remaining 9 concurrent transfers should fail with 422 Insufficient Balance")
				.isEqualTo(9);

		// 5. Verify database balance invariance
		User updatedSender = userRepository.findByUpiId(senderUpi).orElseThrow();
		assertThat(updatedSender.getBalance()).as("Sender final balance must be exactly ₹50.00 (₹150 - ₹100)")
				.isEqualByComparingTo("50.0000");

		// Total receiver balances must equal exactly ₹100.00
		BigDecimal totalReceiverBalance = BigDecimal.ZERO;
		for (String rUpi : receiverUpis) {
			User rUser = userRepository.findByUpiId(rUpi).orElseThrow();
			totalReceiverBalance = totalReceiverBalance.add(rUser.getBalance());
		}
		assertThat(totalReceiverBalance).as("Total money credited across all receivers must equal exactly ₹100.00")
				.isEqualByComparingTo("100.0000");
	}
}
