package com.payflow.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

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

import jakarta.servlet.ServletException;

@ExtendWith(MockitoExtension.class)
class IdempotencyFilterTest {

	@Mock
	private IdempotencyRepository idempotencyRepository;

	private ObjectMapper objectMapper;
	private IdempotencyFilter idempotencyFilter;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
		idempotencyFilter = new IdempotencyFilter(idempotencyRepository, objectMapper);
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
		java.security.MessageDigest digest;
		try {
			digest = java.security.MessageDigest.getInstance("SHA-256");
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
		String hash = java.util.HexFormat.of().formatHex(digest.digest(requestBytes));

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
		java.security.MessageDigest digest;
		try {
			digest = java.security.MessageDigest.getInstance("SHA-256");
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
		String hash = java.util.HexFormat.of().formatHex(digest.digest(requestBytes));

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
}
