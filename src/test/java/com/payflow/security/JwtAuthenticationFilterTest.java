package com.payflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.ServletException;

@DisplayName("JwtAuthenticationFilter Unit Tests")
class JwtAuthenticationFilterTest {

	private JwtTokenProvider jwtTokenProvider;
	private JwtAuthenticationFilter filter;

	@BeforeEach
	void setUp() {
		jwtTokenProvider = mock(JwtTokenProvider.class);
		filter = new JwtAuthenticationFilter(jwtTokenProvider);
		SecurityContextHolder.clearContext();
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("Should pass through when no Authorization header is present")
	void shouldPassThroughWhenNoAuthHeader() throws ServletException, IOException {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain filterChain = new MockFilterChain();

		filter.doFilterInternal(request, response, filterChain);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		verify(jwtTokenProvider, never()).validateToken(org.mockito.ArgumentMatchers.anyString());
	}

	@Test
	@DisplayName("Should pass through when Authorization header does not start with Bearer")
	void shouldPassThroughWhenNotBearer() throws ServletException, IOException {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain filterChain = new MockFilterChain();

		filter.doFilterInternal(request, response, filterChain);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		verify(jwtTokenProvider, never()).validateToken(org.mockito.ArgumentMatchers.anyString());
	}

	@Test
	@DisplayName("Should authenticate user when valid Bearer token is present")
	void shouldAuthenticateWhenValidToken() throws ServletException, IOException {
		MockHttpServletRequest request = new MockHttpServletRequest();
		String token = "valid.jwt.token";
		request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain filterChain = new MockFilterChain();

		when(jwtTokenProvider.validateToken(token)).thenReturn(true);
		when(jwtTokenProvider.getUpiIdFromToken(token)).thenReturn("user@payflow");

		filter.doFilterInternal(request, response, filterChain);

		var auth = SecurityContextHolder.getContext().getAuthentication();
		assertThat(auth).isNotNull();
		assertThat(auth.getName()).isEqualTo("user@payflow");
		assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
	}

	@Test
	@DisplayName("Should not authenticate when Bearer token is invalid")
	void shouldNotAuthenticateWhenInvalidToken() throws ServletException, IOException {
		MockHttpServletRequest request = new MockHttpServletRequest();
		String token = "invalid.token";
		request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain filterChain = new MockFilterChain();

		when(jwtTokenProvider.validateToken(token)).thenReturn(false);

		filter.doFilterInternal(request, response, filterChain);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}
}
