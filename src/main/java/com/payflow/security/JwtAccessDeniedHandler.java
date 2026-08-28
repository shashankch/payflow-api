package com.payflow.security;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

	private final ObjectMapper objectMapper;

	public JwtAccessDeniedHandler(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {

		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setStatus(HttpStatus.FORBIDDEN.value());

		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
				accessDeniedException.getMessage());
		problem.setType(URI.create("https://api.payflow.com/errors/access-denied"));
		problem.setTitle("Access Denied");
		problem.setInstance(URI.create(request.getRequestURI()));
		problem.setProperty("timestamp", Instant.now().toString());

		response.getWriter().write(objectMapper.writeValueAsString(problem));
	}
}
