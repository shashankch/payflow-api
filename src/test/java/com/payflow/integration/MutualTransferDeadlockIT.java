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
	@DisplayName("Should prevent database deadlocks when two users perform mutual transfers simultaneously")
	void shouldPreventDeadlock_whenMutualTransfersExecuteSimultaneously() throws InterruptedException {
		// 1. Create User A (₹500.00) and User B (₹500.00)
		String upiA = "userA.deadlock@payflow";
		String upiB = "userB.deadlock@payflow";

		CreateUserRequest userAReq = new CreateUserRequest();
		userAReq.setName("User A");
		userAReq.setUpiId(upiA);
		userAReq.setPhoneNumber("9777111111");
		userAReq.setBalance(new BigDecimal("500.0000"));
		restTemplate.postForEntity("/api/v1/users", userAReq, UserResponse.class);

		CreateUserRequest userBReq = new CreateUserRequest();
		userBReq.setName("User B");
		userBReq.setUpiId(upiB);
		userBReq.setPhoneNumber("9777222222");
		userBReq.setBalance(new BigDecimal("500.0000"));
		restTemplate.postForEntity("/api/v1/users", userBReq, UserResponse.class);

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

				HttpHeaders headers = new HttpHeaders();
				headers.set(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString());
				HttpEntity<TransferMoneyRequest> txEntity = new HttpEntity<>(req, headers);

				ResponseEntity<TransactionResponse> res = restTemplate.postForEntity("/api/v1/transactions", txEntity,
						TransactionResponse.class);
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

				HttpHeaders headers = new HttpHeaders();
				headers.set(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString());
				HttpEntity<TransferMoneyRequest> txEntity = new HttpEntity<>(req, headers);

				ResponseEntity<TransactionResponse> res = restTemplate.postForEntity("/api/v1/transactions", txEntity,
						TransactionResponse.class);
				if (res.getStatusCode() == HttpStatus.CREATED) {
					successCount.incrementAndGet();
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} finally {
				finishLatch.countDown();
			}
		});

		readyLatch.await(5, TimeUnit.SECONDS);
		startLatch.countDown();
		finishLatch.await(10, TimeUnit.SECONDS);
		executor.shutdown();
		executor.awaitTermination(5, TimeUnit.SECONDS);

		// 3. Assert both mutual transfers completed cleanly with zero deadlocks
		assertThat(successCount.get()).as("Both mutual transfers must succeed").isEqualTo(2);

		// 4. Assert balances returned to initial state (500 - 100 + 100 = 500)
		User finalA = userRepository.findByUpiId(upiA).orElseThrow();
		User finalB = userRepository.findByUpiId(upiB).orElseThrow();

		assertThat(finalA.getBalance()).isEqualByComparingTo("500.0000");
		assertThat(finalB.getBalance()).isEqualByComparingTo("500.0000");
	}
}
