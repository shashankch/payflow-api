package com.payflow.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.payflow.AbstractIntegrationTest;
import com.payflow.config.CacheConfig;
import com.payflow.dto.request.CreateUserRequest;
import com.payflow.dto.request.LoginRequest;
import com.payflow.dto.request.TransferMoneyRequest;
import com.payflow.dto.response.AuthResponse;
import com.payflow.dto.response.TransactionResponse;
import com.payflow.dto.response.UserResponse;
import com.payflow.filter.IdempotencyFilter;

class CacheIT extends AbstractIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private CacheManager cacheManager;

	@Test
	@DisplayName("Should cache user lookup and evict caches upon fund transfer")
	void shouldCacheUserAndEvictOnTransfer() {
		// 1. Register Users
		String senderUpi = "cache.alice@payflow";
		CreateUserRequest senderReq = new CreateUserRequest("Cache Alice", senderUpi, "9777111222",
				new BigDecimal("2000.0000"));
		ResponseEntity<UserResponse> senderCreated = restTemplate.postForEntity("/api/v1/users", senderReq,
				UserResponse.class);
		assertThat(senderCreated.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(senderCreated.getBody()).isNotNull();
		UUID senderRefId = senderCreated.getBody().referenceId();

		String receiverUpi = "cache.bob@payflow";
		CreateUserRequest receiverReq = new CreateUserRequest("Cache Bob", receiverUpi, "9777111223",
				new BigDecimal("1000.0000"));
		ResponseEntity<UserResponse> receiverCreated = restTemplate.postForEntity("/api/v1/users", receiverReq,
				UserResponse.class);
		assertThat(receiverCreated.getStatusCode()).isEqualTo(HttpStatus.CREATED);

		// 2. Authenticate
		LoginRequest login = new LoginRequest(senderUpi);
		ResponseEntity<AuthResponse> authResp = restTemplate.postForEntity("/api/v1/auth/login", login,
				AuthResponse.class);
		assertThat(authResp.getBody()).isNotNull();
		String token = authResp.getBody().accessToken();

		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		HttpEntity<Void> entity = new HttpEntity<>(headers);

		// 3. Query user profile via REST endpoint
		ResponseEntity<UserResponse> fetch1 = restTemplate.exchange("/api/v1/users/" + senderRefId, HttpMethod.GET,
				entity, UserResponse.class);
		assertThat(fetch1.getStatusCode()).isEqualTo(HttpStatus.OK);

		// 4. Verify cache entry is populated in CacheManager
		Cache usersCache = cacheManager.getCache(CacheConfig.CACHE_USERS);
		assertThat(usersCache).isNotNull();
		assertThat(usersCache.get("ref:" + senderRefId)).isNotNull();

		// 5. Execute transfer to trigger cache eviction
		headers.set(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString());
		TransferMoneyRequest transferReq = new TransferMoneyRequest(senderUpi, receiverUpi, new BigDecimal("100.0000"),
				"Cache Eviction Verification");
		HttpEntity<TransferMoneyRequest> txEntity = new HttpEntity<>(transferReq, headers);

		ResponseEntity<TransactionResponse> txResp = restTemplate.exchange("/api/v1/transactions", HttpMethod.POST,
				txEntity, TransactionResponse.class);
		assertThat(txResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

		// 6. Verify cache entry was evicted by @CacheEvict
		assertThat(usersCache.get("ref:" + senderRefId)).isNull();
	}
}
