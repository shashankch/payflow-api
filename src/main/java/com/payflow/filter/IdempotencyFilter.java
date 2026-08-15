package com.payflow.filter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import tools.jackson.databind.ObjectMapper;
import com.payflow.entity.IdempotencyRecord;
import com.payflow.entity.IdempotencyStatus;
import com.payflow.repository.IdempotencyRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class IdempotencyFilter extends OncePerRequestFilter {

	public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
	private static final Logger LOG = LoggerFactory.getLogger(IdempotencyFilter.class);

	private final IdempotencyRepository idempotencyRepository;
	private final ObjectMapper objectMapper;

	public IdempotencyFilter(IdempotencyRepository idempotencyRepository, ObjectMapper objectMapper) {
		this.idempotencyRepository = idempotencyRepository;
		this.objectMapper = objectMapper;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		String method = request.getMethod();
		return !"POST".equalsIgnoreCase(method) || !path.startsWith("/api/v1/transactions");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
			throws ServletException, IOException {

		String uri = req.getRequestURI();
		String key = req.getHeader(IDEMPOTENCY_KEY_HEADER);
		if (key == null || key.isBlank()) {
			LOG.warn("Rejected request missing Idempotency-Key: path={}", uri);
			String msg = "Idempotency-Key header is mandatory.";
			sendError(res, HttpStatus.BAD_REQUEST, "Missing Key", msg, uri);
			return;
		}

		byte[] requestBytes = req.getInputStream().readAllBytes();
		String requestHash = computeSha256(requestBytes);
		CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(req, requestBytes);

		Optional<IdempotencyRecord> existingOpt = idempotencyRepository.findById(key);
		if (existingOpt.isPresent()) {
			IdempotencyRecord existing = existingOpt.get();

			if (!existing.getRequestHash().equals(requestHash)) {
				LOG.warn("Idempotency key reuse: key={}", key);
				String msg = "Key reused with different payload.";
				sendError(res, HttpStatus.BAD_REQUEST, "Key Reuse", msg, uri);
				return;
			}

			if (existing.getStatus() == IdempotencyStatus.PROCESSING
					|| existing.getStatus() == IdempotencyStatus.INITIATED) {
				LOG.warn("Concurrent request in-flight: key={}", key);
				String msg = "Request with key is in-flight.";
				sendError(res, HttpStatus.CONFLICT, "In Flight", msg, uri);
				return;
			}

			if (existing.getStatus() == IdempotencyStatus.SUCCESS) {
				LOG.info("Replaying cached response: key={}", key);
				Integer code = existing.getResponseCode();
				res.setStatus(code != null ? code : 200);
				res.setContentType(MediaType.APPLICATION_JSON_VALUE);
				if (existing.getResponseBody() != null) {
					res.getWriter().write(existing.getResponseBody());
				}
				return;
			}
		}

		IdempotencyRecord record = new IdempotencyRecord();
		record.setIdempotencyKey(key);
		record.setRequestHash(requestHash);
		record.setStatus(IdempotencyStatus.PROCESSING);
		idempotencyRepository.saveAndFlush(record);

		ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(res);
		try {
			chain.doFilter(wrappedRequest, wrappedResponse);
			int statusCode = wrappedResponse.getStatus();
			byte[] responseBytes = wrappedResponse.getContentAsByteArray();
			String responseBody = new String(responseBytes, StandardCharsets.UTF_8);

			boolean is2xx = statusCode >= 200 && statusCode < 300;
			record.setStatus(is2xx ? IdempotencyStatus.SUCCESS : IdempotencyStatus.FAILED);
			record.setResponseCode(statusCode);
			record.setResponseBody(responseBody);
			idempotencyRepository.save(record);

			wrappedResponse.copyBodyToResponse();
		} catch (Exception ex) {
			record.setStatus(IdempotencyStatus.FAILED);
			idempotencyRepository.save(record);
			throw ex;
		}
	}

	private String computeSha256(byte[] data) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(data);
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 algorithm not available", e);
		}
	}

	private void sendError(HttpServletResponse res, HttpStatus status, String title, String detail, String uri)
			throws IOException {
		res.setStatus(status.value());
		res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		problem.setInstance(URI.create(uri));
		problem.setProperty("timestamp", Instant.now().toString());
		res.getWriter().write(objectMapper.writeValueAsString(problem));
	}

	private static class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
		private final byte[] cachedBody;

		CachedBodyHttpServletRequest(HttpServletRequest request, byte[] cachedBody) {
			super(request);
			this.cachedBody = cachedBody;
		}

		@Override
		public ServletInputStream getInputStream() {
			ByteArrayInputStream bais = new ByteArrayInputStream(cachedBody);
			return new ServletInputStream() {
				@Override
				public boolean isFinished() {
					return bais.available() == 0;
				}

				@Override
				public boolean isReady() {
					return true;
				}

				@Override
				public void setReadListener(ReadListener readListener) {
				}

				@Override
				public int read() {
					return bais.read();
				}
			};
		}

		@Override
		public BufferedReader getReader() {
			return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
		}
	}
}
