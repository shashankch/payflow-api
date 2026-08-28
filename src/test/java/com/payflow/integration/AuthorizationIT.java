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

class AuthorizationIT extends AbstractIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	@DisplayName("Should enforce strict sender verification, transaction visibility, and ledger ownership (403 Forbidden on mismatch)")
	void shouldEnforceStrictPrincipalBoundAuthorization() {
		// 1. Register Alice, Bob, and Mallory
		UserResponse alice = registerUser("Alice Auth", "alice.auth@payflow", "9111000001", new BigDecimal("1000.00"));
		UserResponse bob = registerUser("Bob Auth", "bob.auth@payflow", "9111000002", new BigDecimal("500.00"));
		UserResponse mallory = registerUser("Mallory Auth", "mallory.auth@payflow", "9111000003",
				new BigDecimal("300.00"));

		// 2. Login to get tokens for Alice and Mallory
		String aliceToken = loginAndGetToken("alice.auth@payflow");
		String malloryToken = loginAndGetToken("mallory.auth@payflow");
		String bobToken = loginAndGetToken("bob.auth@payflow");

		// 3. Mallory attempts to transfer money using Alice's account as sender -> 403
		// Forbidden
		HttpHeaders malloryHeaders = new HttpHeaders();
		malloryHeaders.setBearerAuth(malloryToken);
		malloryHeaders.set(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, "idemp-auth-" + UUID.randomUUID());

		TransferMoneyRequest impersonateTransfer = new TransferMoneyRequest();
		impersonateTransfer.setSenderUpiId("alice.auth@payflow");
		impersonateTransfer.setReceiverUpiId("mallory.auth@payflow");
		impersonateTransfer.setAmount(new BigDecimal("100.00"));

		HttpEntity<TransferMoneyRequest> impersonateEntity = new HttpEntity<>(impersonateTransfer, malloryHeaders);
		ResponseEntity<String> forbiddenTransferRes = restTemplate.exchange("/api/v1/transactions", HttpMethod.POST,
				impersonateEntity, String.class);

		assertThat(forbiddenTransferRes.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(forbiddenTransferRes.getBody()).contains("Forbidden Operation");

		// 4. Mallory attempts to view Alice's double-entry balance ledger -> 403
		// Forbidden
		HttpEntity<Void> malloryGetEntity = new HttpEntity<>(malloryHeaders);
		ResponseEntity<String> forbiddenLedgerRes = restTemplate.exchange(
				"/api/v1/users/" + alice.referenceId() + "/ledger", HttpMethod.GET, malloryGetEntity, String.class);

		assertThat(forbiddenLedgerRes.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(forbiddenLedgerRes.getBody()).contains("Forbidden Operation");

		// 5. Mallory attempts to view Alice's user profile by ID and by UPI -> 403
		// Forbidden
		ResponseEntity<String> forbiddenProfileById = restTemplate.exchange("/api/v1/users/" + alice.referenceId(),
				HttpMethod.GET, malloryGetEntity, String.class);
		assertThat(forbiddenProfileById.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

		ResponseEntity<String> forbiddenProfileByUpi = restTemplate.exchange("/api/v1/users/upi/alice.auth@payflow",
				HttpMethod.GET, malloryGetEntity, String.class);
		assertThat(forbiddenProfileByUpi.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

		// 6. Alice views her own profile and ledger -> 200 OK
		HttpHeaders aliceHeaders = new HttpHeaders();
		aliceHeaders.setBearerAuth(aliceToken);
		aliceHeaders.set(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, "idemp-auth-" + UUID.randomUUID());
		HttpEntity<Void> aliceGetEntity = new HttpEntity<>(aliceHeaders);

		ResponseEntity<UserResponse> aliceProfileRes = restTemplate.exchange("/api/v1/users/" + alice.referenceId(),
				HttpMethod.GET, aliceGetEntity, UserResponse.class);
		assertThat(aliceProfileRes.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(aliceProfileRes.getBody()).isNotNull();
		assertThat(aliceProfileRes.getBody().upiId()).isEqualTo("alice.auth@payflow");

		ResponseEntity<String> aliceLedgerRes = restTemplate.exchange(
				"/api/v1/users/" + alice.referenceId() + "/ledger", HttpMethod.GET, aliceGetEntity, String.class);
		assertThat(aliceLedgerRes.getStatusCode()).isEqualTo(HttpStatus.OK);

		// 7. Alice executes authorized transfer from Alice to Bob -> 201 Created
		TransferMoneyRequest validTransfer = new TransferMoneyRequest();
		validTransfer.setSenderUpiId("alice.auth@payflow");
		validTransfer.setReceiverUpiId("bob.auth@payflow");
		validTransfer.setAmount(new BigDecimal("150.00"));
		validTransfer.setNote("Authorized split");

		HttpEntity<TransferMoneyRequest> validTxEntity = new HttpEntity<>(validTransfer, aliceHeaders);
		ResponseEntity<TransactionResponse> validTxRes = restTemplate.exchange("/api/v1/transactions", HttpMethod.POST,
				validTxEntity, TransactionResponse.class);

		assertThat(validTxRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(validTxRes.getBody()).isNotNull();
		UUID transactionRefId = validTxRes.getBody().referenceId();

		// 8. Transaction participant visibility:
		// Alice (Sender) can view transaction -> 200 OK
		ResponseEntity<TransactionResponse> senderViewRes = restTemplate.exchange(
				"/api/v1/transactions/" + transactionRefId, HttpMethod.GET, aliceGetEntity, TransactionResponse.class);
		assertThat(senderViewRes.getStatusCode()).isEqualTo(HttpStatus.OK);

		// Bob (Receiver) can view transaction -> 200 OK
		HttpHeaders bobHeaders = new HttpHeaders();
		bobHeaders.setBearerAuth(bobToken);
		HttpEntity<Void> bobGetEntity = new HttpEntity<>(bobHeaders);
		ResponseEntity<TransactionResponse> receiverViewRes = restTemplate.exchange(
				"/api/v1/transactions/" + transactionRefId, HttpMethod.GET, bobGetEntity, TransactionResponse.class);
		assertThat(receiverViewRes.getStatusCode()).isEqualTo(HttpStatus.OK);

		// Mallory (Unrelated third party) cannot view transaction -> 403 Forbidden
		ResponseEntity<String> thirdPartyViewRes = restTemplate.exchange("/api/v1/transactions/" + transactionRefId,
				HttpMethod.GET, malloryGetEntity, String.class);
		assertThat(thirdPartyViewRes.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

		// Mallory cannot view Alice's transactions list -> 403 Forbidden
		ResponseEntity<String> thirdPartyTxListRes = restTemplate.exchange(
				"/api/v1/transactions/user/alice.auth@payflow", HttpMethod.GET, malloryGetEntity, String.class);
		assertThat(thirdPartyTxListRes.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
	}

	private UserResponse registerUser(String name, String upiId, String phone, BigDecimal balance) {
		CreateUserRequest req = new CreateUserRequest();
		req.setName(name);
		req.setUpiId(upiId);
		req.setPhoneNumber(phone);
		req.setBalance(balance);
		ResponseEntity<UserResponse> res = restTemplate.postForEntity("/api/v1/users", req, UserResponse.class);
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		return res.getBody();
	}

	private String loginAndGetToken(String upiId) {
		LoginRequest loginReq = new LoginRequest(upiId);
		ResponseEntity<AuthResponse> res = restTemplate.postForEntity("/api/v1/auth/login", loginReq,
				AuthResponse.class);
		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(res.getBody()).isNotNull();
		return res.getBody().accessToken();
	}
}
