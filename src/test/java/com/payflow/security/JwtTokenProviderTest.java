package com.payflow.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

	private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
	private static final long EXPIRATION_MS = 3600000;

	private JwtTokenProvider jwtTokenProvider;

	@BeforeEach
	void setUp() {
		jwtTokenProvider = new JwtTokenProvider(SECRET, EXPIRATION_MS);
	}

	@Test
	@DisplayName("Should generate valid JWT token with subject and referenceId claims")
	void shouldGenerateValidToken() {
		String upiId = "alice@payflow";
		UUID refId = UUID.randomUUID();

		String token = jwtTokenProvider.generateToken(upiId, refId);

		assertThat(token).isNotBlank();
		assertThat(jwtTokenProvider.validateToken(token)).isTrue();
		assertThat(jwtTokenProvider.getUpiIdFromToken(token)).isEqualTo(upiId);
		assertThat(jwtTokenProvider.getReferenceIdFromToken(token)).isEqualTo(refId);
		assertThat(jwtTokenProvider.getExpirationSeconds()).isEqualTo(3600);
	}

	@Test
	@DisplayName("Should reject malformed or tampered JWT token")
	void shouldRejectTamperedToken() {
		String token = jwtTokenProvider.generateToken("alice@payflow", UUID.randomUUID());
		String tamperedToken = token + "xyz";

		assertThat(jwtTokenProvider.validateToken(tamperedToken)).isFalse();
	}

	@Test
	@DisplayName("Should reject expired JWT token")
	void shouldRejectExpiredToken() {
		// Set expiration to negative value for instant expiry
		JwtTokenProvider expiredProvider = new JwtTokenProvider(SECRET, -1000);
		String token = expiredProvider.generateToken("bob@payflow", UUID.randomUUID());

		assertThat(jwtTokenProvider.validateToken(token)).isFalse();
	}
}
