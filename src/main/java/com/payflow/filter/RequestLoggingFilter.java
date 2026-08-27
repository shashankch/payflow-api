package com.payflow.filter;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class RequestLoggingFilter extends OncePerRequestFilter {

	private static final Logger LOG = LoggerFactory.getLogger(RequestLoggingFilter.class);

	public static final String MDC_HTTP_STATUS = "http.status";
	public static final String MDC_HTTP_METHOD = "http.method";
	public static final String MDC_HTTP_URI = "http.uri";
	public static final String MDC_HTTP_LATENCY_MS = "http.latency_ms";

	@Override
	protected void doFilterInternal(HttpServletRequest request, //
			HttpServletResponse response, //
			FilterChain filterChain) throws ServletException, IOException {
		long startTime = System.currentTimeMillis();
		String method = request.getMethod();
		String uri = request.getRequestURI();

		try {
			filterChain.doFilter(request, response);
		} finally {
			long duration = System.currentTimeMillis() - startTime;
			int status = response.getStatus();

			MDC.put(MDC_HTTP_STATUS, String.valueOf(status));
			MDC.put(MDC_HTTP_METHOD, method);
			MDC.put(MDC_HTTP_URI, uri);
			MDC.put(MDC_HTTP_LATENCY_MS, String.valueOf(duration));

			LOG.info("HTTP {} {} - {} ({}ms)", method, uri, status, duration);

			MDC.remove(MDC_HTTP_STATUS);
			MDC.remove(MDC_HTTP_METHOD);
			MDC.remove(MDC_HTTP_URI);
			MDC.remove(MDC_HTTP_LATENCY_MS);
		}
	}
}
