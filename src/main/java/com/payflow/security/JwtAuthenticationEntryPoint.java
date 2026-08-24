package com.payflow.security;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private static final Logger LOG = LoggerFactory.getLogger(JwtAuthenticationEntryPoint.class);

	private final ObjectMapper objectMapper;

	public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {

		String uri = request.getRequestURI();
		LOG.warn("Unauthorized access attempt: path={}, error={}", uri, authException.getMessage());

		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
				"Full authentication is required to access this resource");
		problem.setType(URI.create("https://api.payflow.com/errors/unauthorized"));
		problem.setTitle("Unauthorized Access");
		problem.setInstance(URI.create(uri));
		problem.setProperty("timestamp", Instant.now().toString());

		String requestId = MDC.get("requestId");
		if (requestId != null) {
			problem.setProperty("requestId", requestId);
		}

		response.getWriter().write(objectMapper.writeValueAsString(problem));
	}
}
