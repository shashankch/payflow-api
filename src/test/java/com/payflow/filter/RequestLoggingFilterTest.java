package com.payflow.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class RequestLoggingFilterTest {

	private final RequestLoggingFilter filter = new RequestLoggingFilter();

	@Test
	@DisplayName("Should execute request through filter chain and cleanup MDC after logging")
	void shouldLogRequestAndCleanupMdc() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/transactions");
		MockHttpServletResponse response = new MockHttpServletResponse();
		response.setStatus(201);
		MockFilterChain filterChain = new MockFilterChain();

		filter.doFilterInternal(request, response, filterChain);

		assertThat(MDC.get(RequestLoggingFilter.MDC_HTTP_STATUS)).isNull();
		assertThat(MDC.get(RequestLoggingFilter.MDC_HTTP_METHOD)).isNull();
		assertThat(MDC.get(RequestLoggingFilter.MDC_HTTP_URI)).isNull();
		assertThat(MDC.get(RequestLoggingFilter.MDC_HTTP_LATENCY_MS)).isNull();
	}

	@Test
	@DisplayName("Should invoke filter chain and handle exceptions cleanly while clearing MDC")
	void shouldHandleExceptionsAndClearMdc() throws Exception {
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);
		FilterChain filterChain = mock(FilterChain.class);

		when(request.getMethod()).thenReturn("GET");
		when(request.getRequestURI()).thenReturn("/api/v1/users");
		when(response.getStatus()).thenReturn(500);

		filter.doFilterInternal(request, response, filterChain);

		verify(filterChain).doFilter(request, response);
		assertThat(MDC.get(RequestLoggingFilter.MDC_HTTP_STATUS)).isNull();
	}
}
