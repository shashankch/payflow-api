package com.payflow.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtTokenProvider {

	private static final Logger LOG = LoggerFactory.getLogger(JwtTokenProvider.class);
	public static final String DEFAULT_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

	private final SecretKey secretKey;
	private final long expirationMs;

	@Autowired
	public JwtTokenProvider( //
			@Value("${payflow.security.jwt.secret:" + DEFAULT_SECRET + "}") String secret,
			@Value("${payflow.security.jwt.expiration-ms:3600000}") long expirationMs, //
			Environment environment) {
		if (environment != null && environment.matchesProfiles("prod")) {
			boolean isWeakSecret = secret == null || secret.isBlank() || DEFAULT_SECRET.equals(secret)
					|| secret.length() < 32;
			if (isWeakSecret) {
				String msg = "Production environment requires external PAYFLOW_SECURITY_JWT_SECRET "
						+ "of >= 256 bits. Default secret is prohibited.";
				throw new IllegalStateException(msg);
			}
		}
		this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.expirationMs = expirationMs;
	}

	public JwtTokenProvider(String secret, long expirationMs) {
		this(secret, expirationMs, null);
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
