package com.payflow.resilience;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;

/**
 * Service managing dynamic per-user rate limiters with memory-leak protection.
 * Each user partition is dynamically registered using the base configuration
 * template and evicted when inactive.
 */
@Service
public class UserRateLimiterService {

	private static final Logger LOG = LoggerFactory.getLogger(UserRateLimiterService.class);
	private static final long INACTIVITY_THRESHOLD_MS = 15 * 60 * 1000L; // 15 minutes

	private final RateLimiterRegistry rateLimiterRegistry;
	private final Map<String, Long> lastAccessTimestampMap = new ConcurrentHashMap<>();

	public UserRateLimiterService(RateLimiterRegistry rateLimiterRegistry) {
		this.rateLimiterRegistry = rateLimiterRegistry;
	}

	/**
	 * Retrieves or registers a per-user RateLimiter instance using the specified
	 * base configuration template.
	 *
	 * @param baseConfigName
	 *            the base rate limiter configuration name in Resilience4j (e.g.,
	 *            'transferLimiter')
	 * @param userKey
	 *            the resolved partition key for the user
	 * @return the RateLimiter instance dedicated to this user partition
	 */
	public RateLimiter getRateLimiterForUser(String baseConfigName, String userKey) {
		String dynamicName = baseConfigName + ":" + userKey;
		lastAccessTimestampMap.put(dynamicName, Instant.now().toEpochMilli());
		try {
			return rateLimiterRegistry.rateLimiter(dynamicName, baseConfigName);
		} catch (io.github.resilience4j.core.ConfigurationNotFoundException ex) {
			return rateLimiterRegistry.rateLimiter(dynamicName);
		}
	}

	/**
	 * Attempts to acquire permission for the specified user partition.
	 *
	 * @param baseConfigName
	 *            the base configuration name
	 * @param userKey
	 *            the user partition key
	 * @return true if permission was acquired, false if rate limit is exceeded
	 */
	public boolean acquirePermission(String baseConfigName, String userKey) {
		RateLimiter rateLimiter = getRateLimiterForUser(baseConfigName, userKey);
		return rateLimiter.acquirePermission();
	}

	/**
	 * Executes a supplier decorated with the per-user rate limiter.
	 */
	public <T> T executeWithRateLimit(String baseConfigName, String userKey, Supplier<T> supplier) {
		RateLimiter rateLimiter = getRateLimiterForUser(baseConfigName, userKey);
		return RateLimiter.decorateSupplier(rateLimiter, supplier).get();
	}

	/**
	 * Executes a runnable decorated with the per-user rate limiter.
	 */
	public void executeWithRateLimit(String baseConfigName, String userKey, Runnable runnable) {
		RateLimiter rateLimiter = getRateLimiterForUser(baseConfigName, userKey);
		RateLimiter.decorateRunnable(rateLimiter, runnable).run();
	}

	/**
	 * Scheduled task running every 5 minutes to evict rate limiters inactive for
	 * >15 minutes, preventing unbounded memory accumulation.
	 */
	@Scheduled(fixedRate = 300_000)
	public void evictInactiveLimiters() {
		long now = Instant.now().toEpochMilli();
		lastAccessTimestampMap.forEach((dynamicName, lastAccessTime) -> {
			if (now - lastAccessTime > INACTIVITY_THRESHOLD_MS) {
				LOG.debug("Evicting inactive rate limiter instance: {}", dynamicName);
				rateLimiterRegistry.remove(dynamicName);
				lastAccessTimestampMap.remove(dynamicName);
			}
		});
	}

	/**
	 * Clears all registered dynamic rate limiters (primarily for testing purposes).
	 */
	public void clear() {
		lastAccessTimestampMap.keySet().forEach(rateLimiterRegistry::remove);
		lastAccessTimestampMap.clear();
	}
}
