package com.payflow.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.ServletException;

class RequestIdFilterTest {

	private RequestIdFilter filter;

	@BeforeEach
	void setUp() {
		filter = new RequestIdFilter();
	}

	@Test
	void testDoFilterInternalGeneratesRequestIdWhenMissing() throws ServletException, IOException {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain filterChain = new MockFilterChain();

		filter.doFilterInternal(request, response, filterChain);

		String headerValue = response.getHeader(RequestIdFilter.REQUEST_ID_HEADER);
		assertNotNull(headerValue);
		assertNull(MDC.get(RequestIdFilter.MDC_KEY));
	}

	@Test
	void testDoFilterInternalPreservesExistingRequestId() throws ServletException, IOException {
		MockHttpServletRequest request = new MockHttpServletRequest();
		String customId = "custom-req-id-12345";
		request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, customId);
		MockHttpServletResponse response = new MockHttpServletResponse();

		MockFilterChain filterChain = new MockFilterChain() {
			@Override
			public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res)
					throws IOException, ServletException {
				assertEquals(customId, MDC.get(RequestIdFilter.MDC_KEY));
				super.doFilter(req, res);
			}
		};

		filter.doFilterInternal(request, response, filterChain);

		String headerValue = response.getHeader(RequestIdFilter.REQUEST_ID_HEADER);
		assertEquals(customId, headerValue);
		assertNull(MDC.get(RequestIdFilter.MDC_KEY));
	}
}
