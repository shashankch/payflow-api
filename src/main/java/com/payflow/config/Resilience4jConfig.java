package com.payflow.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.payflow.security.SecurityUtils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resilience4j configuration setting up AOP auto-proxying and rate-limiting key
 * resolution.
 */
@Configuration
@EnableAspectJAutoProxy
public class Resilience4jConfig {

	private static final Logger LOG = LoggerFactory.getLogger(Resilience4jConfig.class);

	/**
	 * Default key resolver for rate limiting. Resolves the authenticated user's UPI
	 * ID, falling back to the client IP address or 'anonymous'.
	 */
	@Bean
	@ConditionalOnMissingBean
	public RateLimiterKeyResolver rateLimiterKeyResolver() {
		return () -> {
			String authenticatedUpi = SecurityUtils.getAuthenticatedUpiId();
			if (authenticatedUpi != null && !authenticatedUpi.isBlank()) {
				return authenticatedUpi;
			}

			RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
			if (attributes instanceof ServletRequestAttributes servletAttributes) {
				HttpServletRequest request = servletAttributes.getRequest();
				String xForwardedFor = request.getHeader("X-Forwarded-For");
				if (xForwardedFor != null && !xForwardedFor.isBlank()) {
					return xForwardedFor.split(",")[0].trim();
				}
				String remoteAddr = request.getRemoteAddr();
				if (remoteAddr != null && !remoteAddr.isBlank()) {
					return remoteAddr;
				}
			}

			LOG.debug("No client IP found; using 'anonymous' rate limit partition");
			return "anonymous";
		};
	}
}
