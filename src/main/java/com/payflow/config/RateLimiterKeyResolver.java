package com.payflow.config;

/**
 * Strategy interface to resolve the partition key for per-user or per-client
 * rate limiting.
 */
@FunctionalInterface
public interface RateLimiterKeyResolver {

	/**
	 * Resolves the rate-limiting key for the current request.
	 *
	 * @return the resolved partition key (e.g., authenticated UPI ID, username,
	 *         client IP, or "anonymous")
	 */
	String resolveKey();
}
