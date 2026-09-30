package com.payflow.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import tools.jackson.databind.ObjectMapper;

@DisplayName("JwtAccessDeniedHandler Unit Tests")
class JwtAccessDeniedHandlerTest {

	private JwtAccessDeniedHandler accessDeniedHandler;
	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
		accessDeniedHandler = new JwtAccessDeniedHandler(objectMapper);
	}

	@Test
	@DisplayName("Should return 403 ProblemDetail when access is denied")
	void shouldReturn403ProblemDetail() throws IOException {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRequestURI("/api/v1/admin/users");
		MockHttpServletResponse response = new MockHttpServletResponse();

		accessDeniedHandler.handle(request, response, new AccessDeniedException("Forbidden access to resource"));

		assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
		assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		assertThat(response.getContentAsString()).contains("Access Denied");
		assertThat(response.getContentAsString()).contains("Forbidden access to resource");
		assertThat(response.getContentAsString()).contains("/api/v1/admin/users");
	}
}
