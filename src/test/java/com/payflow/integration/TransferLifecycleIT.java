package com.payflow.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.payflow.AbstractIntegrationTest;
import com.payflow.dto.request.CreateUserRequest;
import com.payflow.dto.request.TransferMoneyRequest;
import com.payflow.dto.response.LedgerEntryResponse;
import com.payflow.dto.response.PagedResponse;
import com.payflow.dto.response.TransactionResponse;
import com.payflow.dto.response.UserResponse;
import com.payflow.entity.LedgerEntryType;
import com.payflow.entity.TransactionStatus;
import com.payflow.entity.TransactionType;
import com.payflow.entity.User;
import com.payflow.filter.IdempotencyFilter;
import com.payflow.repository.BalanceLedgerRepository;
import com.payflow.repository.UserRepository;

class TransferLifecycleIT extends AbstractIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private BalanceLedgerRepository balanceLedgerRepository;

	@Test
	@DisplayName("Should execute complete money transfer lifecycle against PostgreSQL with double-entry ledger audit")
	void shouldExecuteTransferLifecycle_andVerifyDoubleEntryLedger() {
		// 1. Register Alice (₹1,000.00)
		CreateUserRequest createAlice = new CreateUserRequest();
		createAlice.setName("Alice Lifecycle");
		createAlice.setUpiId("alice.life@payflow");
		createAlice.setPhoneNumber("9876543210");
		createAlice.setBalance(new BigDecimal("1000.0000"));

		ResponseEntity<UserResponse> aliceRes = restTemplate.postForEntity("/api/v1/users", createAlice,
				UserResponse.class);
		assertThat(aliceRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(aliceRes.getBody()).isNotNull();
		UUID aliceRefId = aliceRes.getBody().referenceId();

		// 2. Register Bob (₹500.00)
		CreateUserRequest createBob = new CreateUserRequest();
		createBob.setName("Bob Lifecycle");
		createBob.setUpiId("bob.life@payflow");
		createBob.setPhoneNumber("9876543211");
		createBob.setBalance(new BigDecimal("500.0000"));

		ResponseEntity<UserResponse> bobRes = restTemplate.postForEntity("/api/v1/users", createBob,
				UserResponse.class);
		assertThat(bobRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(bobRes.getBody()).isNotNull();
		UUID bobRefId = bobRes.getBody().referenceId();

		// 3. Initiate Transfer of ₹300.00 from Alice -> Bob with Idempotency-Key & JWT
		TransferMoneyRequest transferReq = new TransferMoneyRequest();
		transferReq.setSenderUpiId("alice.life@payflow");
		transferReq.setReceiverUpiId("bob.life@payflow");
		transferReq.setAmount(new BigDecimal("300.0000"));
		transferReq.setNote("Lifecycle Test Dinner Split");

		HttpHeaders headers = authHeaders("alice.life@payflow");
		headers.set(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString());
		HttpEntity<TransferMoneyRequest> txEntity = new HttpEntity<>(transferReq, headers);

		ResponseEntity<TransactionResponse> txRes = restTemplate.exchange("/api/v1/transactions", HttpMethod.POST,
				txEntity, TransactionResponse.class);
		assertThat(txRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(txRes.getBody()).isNotNull();

		TransactionResponse tx = txRes.getBody();
		assertThat(tx.status()).isEqualTo(TransactionStatus.COMPLETED);
		assertThat(tx.type()).isEqualTo(TransactionType.TRANSFER);
		assertThat(tx.amount()).isEqualByComparingTo("300.0000");
		assertThat(tx.senderUpiId()).isEqualTo("alice.life@payflow");
		assertThat(tx.receiverUpiId()).isEqualTo("bob.life@payflow");

		URI locationHeader = txRes.getHeaders().getLocation();
		assertThat(locationHeader).isNotNull();
		assertThat(locationHeader.toString()).contains(tx.referenceId().toString());

		// 4. Verify Alice's updated balance (₹700.00) via REST API
		ResponseEntity<UserResponse> updatedAlice = restTemplate.exchange("/api/v1/users/" + aliceRefId, HttpMethod.GET,
				new HttpEntity<>(headers), UserResponse.class);
		assertThat(updatedAlice.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(updatedAlice.getBody()).isNotNull();
		assertThat(updatedAlice.getBody().balance()).isEqualByComparingTo("700.0000");

		// 5. Verify Bob's updated balance (₹800.00) via REST API
		ResponseEntity<UserResponse> updatedBob = restTemplate.exchange("/api/v1/users/" + bobRefId, HttpMethod.GET,
				new HttpEntity<>(headers), UserResponse.class);
		assertThat(updatedBob.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(updatedBob.getBody()).isNotNull();
		assertThat(updatedBob.getBody().balance()).isEqualByComparingTo("800.0000");

		// 6. Verify Alice's Balance Ledger audit logs (DEBIT ₹300.00)
		ResponseEntity<PagedResponse<LedgerEntryResponse>> aliceLedgerRes = restTemplate.exchange(
				"/api/v1/users/" + aliceRefId + "/ledger", HttpMethod.GET, new HttpEntity<>(headers),
				new ParameterizedTypeReference<PagedResponse<LedgerEntryResponse>>() {
				});
		assertThat(aliceLedgerRes.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(aliceLedgerRes.getBody()).isNotNull();
		assertThat(aliceLedgerRes.getBody().content()).hasSize(1);

		LedgerEntryResponse aliceLedger = aliceLedgerRes.getBody().content().get(0);
		assertThat(aliceLedger.entryType()).isEqualTo(LedgerEntryType.DEBIT);
		assertThat(aliceLedger.amount()).isEqualByComparingTo("300.0000");
		assertThat(aliceLedger.balanceBefore()).isEqualByComparingTo("1000.0000");
		assertThat(aliceLedger.balanceAfter()).isEqualByComparingTo("700.0000");

		// 7. Verify Bob's Balance Ledger audit logs (CREDIT ₹300.00)
		ResponseEntity<PagedResponse<LedgerEntryResponse>> bobLedgerRes = restTemplate.exchange(
				"/api/v1/users/" + bobRefId + "/ledger", HttpMethod.GET, new HttpEntity<>(headers),
				new ParameterizedTypeReference<PagedResponse<LedgerEntryResponse>>() {
				});
		assertThat(bobLedgerRes.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(bobLedgerRes.getBody()).isNotNull();
		assertThat(bobLedgerRes.getBody().content()).hasSize(1);

		LedgerEntryResponse bobLedger = bobLedgerRes.getBody().content().get(0);
		assertThat(bobLedger.entryType()).isEqualTo(LedgerEntryType.CREDIT);
		assertThat(bobLedger.amount()).isEqualByComparingTo("300.0000");
		assertThat(bobLedger.balanceBefore()).isEqualByComparingTo("500.0000");
		assertThat(bobLedger.balanceAfter()).isEqualByComparingTo("800.0000");

		// 8. Assert Aggregate Ledger Reconciliation Query
		User aliceEntity = userRepository.findByUpiId("alice.life@payflow").orElseThrow();
		User bobEntity = userRepository.findByUpiId("bob.life@payflow").orElseThrow();

		BigDecimal aliceReconciled = balanceLedgerRepository
				.calculateReconciledBalanceByUserId(aliceEntity.getUserId());
		BigDecimal bobReconciled = balanceLedgerRepository.calculateReconciledBalanceByUserId(bobEntity.getUserId());

		// Reconciled sum of ledger entries matches balance changes
		assertThat(aliceEntity.getBalance().subtract(new BigDecimal("1000.0000")))
				.isEqualByComparingTo(aliceReconciled);
		assertThat(bobEntity.getBalance().subtract(new BigDecimal("500.0000"))).isEqualByComparingTo(bobReconciled);
	}
}
