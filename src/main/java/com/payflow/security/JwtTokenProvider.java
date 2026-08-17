package com.payflow.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtTokenProvider {

	private static final Logger LOG = LoggerFactory.getLogger(JwtTokenProvider.class);
	private static final String DEFAULT_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

	private final SecretKey secretKey;
	private final long expirationMs;

	public JwtTokenProvider(@Value("${payflow.security.jwt.secret:" + DEFAULT_SECRET + "}") String secret,
			@Value("${payflow.security.jwt.expiration-ms:3600000}") long expirationMs) {
		this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.expirationMs = expirationMs;
	}

	public String generateToken(String upiId, UUID referenceId) {
		Date now = Date.from(Instant.now());
		Date expiry = Date.from(Instant.now().plusMillis(expirationMs));

		var builder = Jwts.builder();
		builder.subject(upiId);
		builder.claim("referenceId", referenceId.toString());
		builder.claim("roles", List.of("ROLE_USER"));
		builder.issuedAt(now);
		builder.expiration(expiry);
		builder.signWith(secretKey);
		return builder.compact();
	}

	public boolean validateToken(String token) {
		try {
			Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
			return true;
		} catch (JwtException | IllegalArgumentException ex) {
			LOG.debug("Invalid JWT token: {}", ex.getMessage());
			return false;
		}
	}

	public String getUpiIdFromToken(String token) {
		Claims claims = getClaims(token);
		return claims.getSubject();
	}

	public UUID getReferenceIdFromToken(String token) {
		Claims claims = getClaims(token);
		String refIdStr = claims.get("referenceId", String.class);
		return UUID.fromString(refIdStr);
	}

	public long getExpirationSeconds() {
		return expirationMs / 1000;
	}

	private Claims getClaims(String token) {
		return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
	}
}
