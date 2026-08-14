package com.payflow.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.payflow.AbstractIntegrationTest;
import com.payflow.dto.request.CreateUserRequest;
import com.payflow.dto.request.TransferMoneyRequest;
import com.payflow.dto.response.TransactionResponse;
import com.payflow.dto.response.UserResponse;
import com.payflow.entity.User;
import com.payflow.repository.BalanceLedgerRepository;
import com.payflow.repository.UserRepository;

class ConcurrentTransferIT extends AbstractIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private BalanceLedgerRepository balanceLedgerRepository;

	@Test
	@DisplayName("Should prevent double-spending under high concurrency (10 simultaneous threads, ₹150 balance)")
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

					ResponseEntity<TransactionResponse> response = restTemplate.postForEntity("/api/v1/transactions",
							txReq, TransactionResponse.class);

					if (response.getStatusCode() == HttpStatus.CREATED) {
						successCount.incrementAndGet();
					} else {
						failureCount.incrementAndGet();
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} finally {
					finishLatch.countDown();
				}
			});
		}

		readyLatch.await(10, TimeUnit.SECONDS);
		startLatch.countDown(); // Release all 10 threads simultaneously
		finishLatch.await(15, TimeUnit.SECONDS);
		executor.shutdown();
		executor.awaitTermination(5, TimeUnit.SECONDS);

		// 4. Assert Concurrency Invariants
		assertThat(successCount.get()).as("Exactly 1 concurrent transfer must succeed").isEqualTo(1);
		assertThat(failureCount.get()).as("Exactly 9 concurrent transfers must fail").isEqualTo(9);

		// 5. Verify database state in PostgreSQL
		User sender = userRepository.findByUpiId(senderUpi).orElseThrow();
		assertThat(sender.getBalance())
				.as("Sender final balance must be exactly 50.0000 (150.00 - 100.00), never negative")
				.isEqualByComparingTo("50.0000");

		// 6. Verify ledger reconciliation
		BigDecimal reconciled = balanceLedgerRepository.calculateReconciledBalanceByUserId(sender.getUserId());
		assertThat(reconciled).as("Reconciled ledger delta must equal -100.0000").isEqualByComparingTo("-100.0000");
	}
}
