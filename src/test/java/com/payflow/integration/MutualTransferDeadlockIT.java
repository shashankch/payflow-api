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

class MutualTransferDeadlockIT extends AbstractIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private UserRepository userRepository;

	@Test
	@DisplayName("Mutual transfers A->B and B->A concurrently execute without deadlocks via deterministic alphabetical row lock ordering")
	void shouldPreventDeadlocks_underConcurrentMutualTransfers() throws InterruptedException {
		// 1. Create User A and User B with ₹500.00 each
		String upiA = "user.alpha@payflow";
		String upiB = "user.beta@payflow";

		CreateUserRequest reqA = new CreateUserRequest();
		reqA.setName("User Alpha");
		reqA.setUpiId(upiA);
		reqA.setPhoneNumber("9876111111");
		reqA.setBalance(new BigDecimal("500.0000"));
		restTemplate.postForEntity("/api/v1/users", reqA, UserResponse.class);

		CreateUserRequest reqB = new CreateUserRequest();
		reqB.setName("User Beta");
		reqB.setUpiId(upiB);
		reqB.setPhoneNumber("9876222222");
		reqB.setBalance(new BigDecimal("500.0000"));
		restTemplate.postForEntity("/api/v1/users", reqB, UserResponse.class);

		// 2. Set up synchronized concurrent mutual transfers (A -> B and B -> A)
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch readyLatch = new CountDownLatch(2);
		CountDownLatch startLatch = new CountDownLatch(1);
		CountDownLatch finishLatch = new CountDownLatch(2);

		AtomicInteger successCount = new AtomicInteger(0);

		// Thread 1: A -> B (₹100)
		executor.submit(() -> {
			readyLatch.countDown();
			try {
				startLatch.await();
				TransferMoneyRequest req = new TransferMoneyRequest();
				req.setSenderUpiId(upiA);
				req.setReceiverUpiId(upiB);
				req.setAmount(new BigDecimal("100.0000"));
				req.setNote("Mutual Transfer A -> B");

				HttpHeaders headers = authHeaders(upiA);
				headers.set(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString());
				HttpEntity<TransferMoneyRequest> txEntity = new HttpEntity<>(req, headers);

				ResponseEntity<TransactionResponse> res = restTemplate.exchange("/api/v1/transactions", HttpMethod.POST,
						txEntity, TransactionResponse.class);
				if (res.getStatusCode() == HttpStatus.CREATED) {
					successCount.incrementAndGet();
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} finally {
				finishLatch.countDown();
			}
		});

		// Thread 2: B -> A (₹100)
		executor.submit(() -> {
			readyLatch.countDown();
			try {
				startLatch.await();
				TransferMoneyRequest req = new TransferMoneyRequest();
				req.setSenderUpiId(upiB);
				req.setReceiverUpiId(upiA);
				req.setAmount(new BigDecimal("100.0000"));
				req.setNote("Mutual Transfer B -> A");

				HttpHeaders headers = authHeaders(upiB);
				headers.set(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString());
				HttpEntity<TransferMoneyRequest> txEntity = new HttpEntity<>(req, headers);

				ResponseEntity<TransactionResponse> res = restTemplate.exchange("/api/v1/transactions", HttpMethod.POST,
						txEntity, TransactionResponse.class);
				if (res.getStatusCode() == HttpStatus.CREATED) {
					successCount.incrementAndGet();
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} finally {
				finishLatch.countDown();
			}
		});

		// Trigger simultaneous execution
		readyLatch.await(5, TimeUnit.SECONDS);
		startLatch.countDown();
		finishLatch.await(10, TimeUnit.SECONDS);
		executor.shutdown();

		// 3. Assert Invariants: Both transfers completed cleanly without deadlock
		// exception
		assertThat(successCount.get()).as("Both mutual transfers must succeed concurrently without database deadlocks")
				.isEqualTo(2);

		// 4. Verify Final Balances remain exactly ₹500.00 each
		User finalA = userRepository.findByUpiId(upiA).orElseThrow();
		User finalB = userRepository.findByUpiId(upiB).orElseThrow();

		assertThat(finalA.getBalance()).as("User A balance remains ₹500.00 (+100 -100)")
				.isEqualByComparingTo("500.0000");

		assertThat(finalB.getBalance()).as("User B balance remains ₹500.00 (+100 -100)")
				.isEqualByComparingTo("500.0000");
	}
}
