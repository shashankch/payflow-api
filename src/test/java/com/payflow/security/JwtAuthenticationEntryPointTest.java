package com.payflow.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import tools.jackson.databind.ObjectMapper;

@DisplayName("JwtAuthenticationEntryPoint Unit Tests")
class JwtAuthenticationEntryPointTest {

	private JwtAuthenticationEntryPoint entryPoint;
	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
		entryPoint = new JwtAuthenticationEntryPoint(objectMapper);
	}

	@Test
	@DisplayName("Should return 401 ProblemDetail when authentication fails")
	void shouldReturn401ProblemDetail() throws IOException {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRequestURI("/api/v1/transactions");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MDC.put("requestId", "req-12345");

		try {
			entryPoint.commence(request, response, new BadCredentialsException("Invalid token"));

			assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
			assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
			assertThat(response.getContentAsString()).contains("Unauthorized Access");
			assertThat(response.getContentAsString()).contains("req-12345");
		} finally {
			MDC.remove("requestId");
		}
	}
}
