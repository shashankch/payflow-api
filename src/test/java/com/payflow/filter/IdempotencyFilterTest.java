package com.payflow.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import tools.jackson.databind.ObjectMapper;
import com.payflow.entity.IdempotencyRecord;
import com.payflow.entity.IdempotencyStatus;
import com.payflow.repository.IdempotencyRepository;
import com.payflow.service.DistributedLockService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

@ExtendWith(MockitoExtension.class)
class IdempotencyFilterTest {

	@Mock
	private IdempotencyRepository idempotencyRepository;

	@Mock
	private DistributedLockService distributedLockService;

	private ObjectMapper objectMapper;
	private IdempotencyFilter idempotencyFilter;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
		lenient().when(distributedLockService.tryLock(any(), any(), any())).thenReturn(true);
		idempotencyFilter = new IdempotencyFilter(idempotencyRepository, objectMapper, distributedLockService);
	}

	@Test
	@DisplayName("Should reject mutation request with 400 Bad Request when Idempotency-Key header is missing")
	void shouldRejectRequest_whenIdempotencyKeyHeaderMissing() throws ServletException, IOException {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/transactions");
		request.setContent("{\"amount\":100}".getBytes(StandardCharsets.UTF_8));
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain filterChain = new MockFilterChain();

		idempotencyFilter.doFilter(request, response, filterChain);

		assertThat(response.getStatus()).isEqualTo(400);
		assertThat(response.getContentAsString()).contains("Missing Key");
		verify(idempotencyRepository, never()).save(any());
	}

	@Test
	@DisplayName("Should reject mutation request with 400 Bad Request when Idempotency-Key exceeds 255 characters")
	void shouldRejectRequest_whenIdempotencyKeyTooLong() throws ServletException, IOException {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/transactions");
		request.addHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, "a".repeat(256));
		request.setContent("{\"amount\":100}".getBytes(StandardCharsets.UTF_8));
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain filterChain = new MockFilterChain();

		idempotencyFilter.doFilter(request, response, filterChain);

		assertThat(response.getStatus()).isEqualTo(400);
		assertThat(response.getContentAsString()).contains("Invalid Key");
		verify(idempotencyRepository, never()).save(any());
	}

	@Test
	@DisplayName("Should reject mutation request with 400 Bad Request when Idempotency-Key contains invalid characters")
	void shouldRejectRequest_whenIdempotencyKeyHasInvalidCharacters() throws ServletException, IOException {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/transactions");
		request.addHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, "invalid key with spaces; DROP TABLE;");
		request.setContent("{\"amount\":100}".getBytes(StandardCharsets.UTF_8));
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain filterChain = new MockFilterChain();

		idempotencyFilter.doFilter(request, response, filterChain);

		assertThat(response.getStatus()).isEqualTo(400);
		assertThat(response.getContentAsString()).contains("Invalid Key");
		verify(idempotencyRepository, never()).save(any());
	}

	@Test
	@DisplayName("Should replay cached response without executing filter chain when key exists with status SUCCESS")
	void shouldReplayCachedResponse_whenKeyExistsWithSuccess() throws ServletException, IOException {
		String payload = "{\"senderUpiId\":\"a@payflow\",\"receiverUpiId\":\"b@payflow\",\"amount\":100}";
		String key = "test-idemp-key-123";

		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/transactions");
		request.addHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key);
		request.setContent(payload.getBytes(StandardCharsets.UTF_8));
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain filterChain = new MockFilterChain();

		// Compute expected hash
		byte[] requestBytes = payload.getBytes(StandardCharsets.UTF_8);
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
		String hash = HexFormat.of().formatHex(digest.digest(requestBytes));

		IdempotencyRecord cachedRecord = IdempotencyRecord.builder().idempotencyKey(key).requestHash(hash)
				.status(IdempotencyStatus.SUCCESS).responseCode(201)
				.responseBody("{\"referenceId\":\"c7a8e8e1-5555-4444-3333-222211110000\",\"status\":\"COMPLETED\"}")
				.build();

		given(idempotencyRepository.findById(key)).willReturn(Optional.of(cachedRecord));

		idempotencyFilter.doFilter(request, response, filterChain);

		assertThat(response.getStatus()).isEqualTo(201);
		assertThat(response.getContentAsString()).contains("c7a8e8e1-5555-4444-3333-222211110000");
		verify(idempotencyRepository, never()).save(any());
	}

	@Test
	@DisplayName("Should reject request with 409 Conflict when key exists with status PROCESSING")
	void shouldRejectWithConflict_whenRequestInProgress() throws ServletException, IOException {
		String payload = "{\"amount\":100}";
		String key = "in-flight-key-456";

		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/transactions");
		request.addHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key);
		request.setContent(payload.getBytes(StandardCharsets.UTF_8));
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain filterChain = new MockFilterChain();

		byte[] requestBytes = payload.getBytes(StandardCharsets.UTF_8);
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
		String hash = HexFormat.of().formatHex(digest.digest(requestBytes));

		IdempotencyRecord inFlightRecord = IdempotencyRecord.builder().idempotencyKey(key).requestHash(hash)
				.status(IdempotencyStatus.PROCESSING).build();

		given(idempotencyRepository.findById(key)).willReturn(Optional.of(inFlightRecord));

		idempotencyFilter.doFilter(request, response, filterChain);

		assertThat(response.getStatus()).isEqualTo(409);
		assertThat(response.getContentAsString()).contains("In Flight");
	}

	@Test
	@DisplayName("Should reject request with 400 Bad Request when key is reused with a different payload hash")
	void shouldRejectWithBadRequest_whenKeyReusedWithDifferentPayload() throws ServletException, IOException {
		String payload = "{\"amount\":200}";
		String key = "reused-key-789";

		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/transactions");
		request.addHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key);
		request.setContent(payload.getBytes(StandardCharsets.UTF_8));
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain filterChain = new MockFilterChain();

		IdempotencyRecord differentHashRecord = IdempotencyRecord.builder().idempotencyKey(key)
				.requestHash("different_sha256_hash_abcdef1234567890").status(IdempotencyStatus.SUCCESS).build();

		given(idempotencyRepository.findById(key)).willReturn(Optional.of(differentHashRecord));

		idempotencyFilter.doFilter(request, response, filterChain);

		assertThat(response.getStatus()).isEqualTo(400);
		assertThat(response.getContentAsString()).contains("Key Reuse");
	}

	@Test
	@DisplayName("Should reject mutation request with 409 Conflict when distributed lock cannot be acquired")
	void shouldRejectRequest_whenDistributedLockCannotBeAcquired() throws ServletException, IOException {
		String payload = "{\"amount\":100}";
		String key = "locked-key-123";

		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/transactions");
		request.addHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key);
		request.setContent(payload.getBytes(StandardCharsets.UTF_8));
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain filterChain = new MockFilterChain();

		given(distributedLockService.tryLock(any(), any(), any())).willReturn(false);

		idempotencyFilter.doFilter(request, response, filterChain);

		assertThat(response.getStatus()).isEqualTo(409);
		assertThat(response.getContentAsString()).contains("Lock Contention");
		verify(idempotencyRepository, never()).findById(any());
		verify(distributedLockService, never()).unlock(any());
	}

	@Test
	@DisplayName("Should always release distributed lock in finally block even when downstream filter throws")
	void shouldAlwaysReleaseLockInFinallyBlock_evenWhenExceptionOccurs() {
		String payload = "{\"amount\":100}";
		String key = "exception-key-999";

		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/transactions");
		request.addHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key);
		request.setContent(payload.getBytes(StandardCharsets.UTF_8));
		MockHttpServletResponse response = new MockHttpServletResponse();

		FilterChain throwingChain = (req, res) -> {
			throw new RuntimeException("Simulated filter failure");
		};

		assertThrows(RuntimeException.class, () -> {
			idempotencyFilter.doFilter(request, response, throwingChain);
		});

		verify(distributedLockService).unlock("payflow:lock:idemp:" + key);
	}

	@Test
	@DisplayName("Should skip filter for GET requests or non-transaction endpoints")
	void shouldNotFilterForNonMutationRequests() {
		MockHttpServletRequest getRequest = new MockHttpServletRequest("GET", "/api/v1/transactions");
		assertThat(idempotencyFilter.shouldNotFilter(getRequest)).isTrue();

		MockHttpServletRequest postNonTxRequest = new MockHttpServletRequest("POST", "/api/v1/auth/login");
		assertThat(idempotencyFilter.shouldNotFilter(postNonTxRequest)).isTrue();

		MockHttpServletRequest postTxRequest = new MockHttpServletRequest("POST", "/api/v1/transactions");
		assertThat(idempotencyFilter.shouldNotFilter(postTxRequest)).isFalse();
	}

	@Test
	@DisplayName("Should process new request successfully and record SUCCESS status")
	void shouldProcessNewRequestSuccessfully() throws ServletException, IOException {
		String payload = "{\"amount\":150}";
		String key = "new-key-101";

		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/transactions");
		request.addHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key);
		request.setContent(payload.getBytes(StandardCharsets.UTF_8));
		MockHttpServletResponse response = new MockHttpServletResponse();

		FilterChain chain = (req, res) -> {
			// Read the request body to verify CachedBodyHttpServletRequest
			ServletInputStream is = req.getInputStream();
			assertThat(is.isReady()).isTrue();
			assertThat(is.read()).isNotEqualTo(-1);
			BufferedReader reader = req.getReader();
			assertThat(reader).isNotNull();

			jakarta.servlet.http.HttpServletResponse httpRes = (jakarta.servlet.http.HttpServletResponse) res;
			httpRes.setStatus(201);
			httpRes.getWriter().write("{\"status\":\"CREATED\"}");
		};

		idempotencyFilter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(201);
		assertThat(response.getContentAsString()).contains("CREATED");
		verify(idempotencyRepository).saveAndFlush(any(IdempotencyRecord.class));
		verify(idempotencyRepository).save(any(IdempotencyRecord.class));
		verify(distributedLockService).unlock("payflow:lock:idemp:" + key);
	}

	@Test
	@DisplayName("Should record FAILED status when downstream response status is 4xx or 5xx")
	void shouldRecordFailedStatusWhenDownstreamFails() throws ServletException, IOException {
		String payload = "{\"amount\":150}";
		String key = "fail-key-202";

		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/transactions");
		request.addHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key);
		request.setContent(payload.getBytes(StandardCharsets.UTF_8));
		MockHttpServletResponse response = new MockHttpServletResponse();

		FilterChain chain = (req, res) -> {
			jakarta.servlet.http.HttpServletResponse httpRes = (jakarta.servlet.http.HttpServletResponse) res;
			httpRes.setStatus(400);
			httpRes.getWriter().write("{\"error\":\"Bad Input\"}");
		};

		idempotencyFilter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(400);
		verify(idempotencyRepository).save(any(IdempotencyRecord.class));
		verify(distributedLockService).unlock("payflow:lock:idemp:" + key);
	}

	@Test
	@DisplayName("Should return 409 Conflict on concurrent insert DataIntegrityViolationException")
	void shouldReturnConflictOnConcurrentInsertRace() throws ServletException, IOException {
		String payload = "{\"amount\":150}";
		String key = "race-key-303";

		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/transactions");
		request.addHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key);
		request.setContent(payload.getBytes(StandardCharsets.UTF_8));
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain filterChain = new MockFilterChain();

		given(idempotencyRepository.saveAndFlush(any()))
				.willThrow(new org.springframework.dao.DataIntegrityViolationException("Unique constraint violation"));

		idempotencyFilter.doFilter(request, response, filterChain);

		assertThat(response.getStatus()).isEqualTo(409);
		assertThat(response.getContentAsString()).contains("Concurrent Conflict");
		verify(distributedLockService).unlock("payflow:lock:idemp:" + key);
	}

	@Test
	@DisplayName("Should allow re-execution when existing record has FAILED status and same hash")
	void shouldAllowReExecutionWhenExistingStatusFailed() throws ServletException, IOException {
		String payload = "{\"amount\":150}";
		String key = "retry-failed-key-404";

		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/transactions");
		request.addHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key);
		request.setContent(payload.getBytes(StandardCharsets.UTF_8));
		MockHttpServletResponse response = new MockHttpServletResponse();

		byte[] requestBytes = payload.getBytes(StandardCharsets.UTF_8);
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
		String hash = HexFormat.of().formatHex(digest.digest(requestBytes));

		IdempotencyRecord failedRecord = IdempotencyRecord.builder().idempotencyKey(key).requestHash(hash)
				.status(IdempotencyStatus.FAILED).build();

		given(idempotencyRepository.findById(key)).willReturn(Optional.of(failedRecord));

		FilterChain chain = (req, res) -> {
			jakarta.servlet.http.HttpServletResponse httpRes = (jakarta.servlet.http.HttpServletResponse) res;
			httpRes.setStatus(200);
			httpRes.getWriter().write("{\"status\":\"SUCCESS\"}");
		};

		idempotencyFilter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(200);
		verify(idempotencyRepository).saveAndFlush(any(IdempotencyRecord.class));
		verify(idempotencyRepository).save(any(IdempotencyRecord.class));
	}

	@Test
	@DisplayName("Should instantiate filter with null DistributedLockService and fall back to NoOp")
	void shouldFallbackToNoOpLockServiceWhenNull() {
		IdempotencyFilter fallbackFilter = new IdempotencyFilter(idempotencyRepository, objectMapper, null);
		assertThat(fallbackFilter).isNotNull();
	}
}
