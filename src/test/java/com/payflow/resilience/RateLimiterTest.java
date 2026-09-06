package com.payflow.resilience;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterTest {

	private RateLimiterRegistry registry;
	private UserRateLimiterService userRateLimiterService;

	@BeforeEach
	void setUp() {
		RateLimiterConfig config = RateLimiterConfig.custom().limitForPeriod(10)
				.limitRefreshPeriod(Duration.ofSeconds(1)).timeoutDuration(Duration.ZERO).build();
		registry = RateLimiterRegistry.of(config);
		userRateLimiterService = new UserRateLimiterService(registry);
	}

	@Test
	@DisplayName("Should permit up to configured threshold for a single user within the refresh period")
	void shouldPermitUpToConfiguredThreshold() {
		String user = "alice@payflow";

		for (int i = 0; i < 10; i++) {
			boolean acquired = userRateLimiterService.acquirePermission("default", user);
			assertThat(acquired).isTrue();
		}

		// 11th request should be rejected
		boolean acquired11th = userRateLimiterService.acquirePermission("default", user);
		assertThat(acquired11th).isFalse();
	}

	@Test
	@DisplayName("Should enforce per-user isolation: throttling User A must not affect User B")
	void shouldEnforcePerUserIsolation() {
		String userA = "alice@payflow";
		String userB = "bob@payflow";

		// Exhaust user A's permissions
		for (int i = 0; i < 10; i++) {
			assertThat(userRateLimiterService.acquirePermission("default", userA)).isTrue();
		}
		assertThat(userRateLimiterService.acquirePermission("default", userA)).isFalse();

		// User B should still have full quota available
		for (int i = 0; i < 10; i++) {
			assertThat(userRateLimiterService.acquirePermission("default", userB)).isTrue();
		}
		assertThat(userRateLimiterService.acquirePermission("default", userB)).isFalse();
	}

	@Test
	@DisplayName("Should throw RequestNotPermitted when decorated supplier exceeds threshold")
	void shouldThrowRequestNotPermittedWhenExceeded() {
		String user = "charlie@payflow";

		for (int i = 0; i < 10; i++) {
			String result = userRateLimiterService.executeWithRateLimit("default", user, () -> "SUCCESS");
			assertThat(result).isEqualTo("SUCCESS");
		}

		assertThatThrownBy(() -> userRateLimiterService.executeWithRateLimit("default", user, () -> "SUCCESS"))
				.isInstanceOf(RequestNotPermitted.class);
	}

	@Test
	@DisplayName("Should clear all dynamic limiters from registry")
	void shouldClearAllLimiters() {
		userRateLimiterService.acquirePermission("default", "user1@payflow");
		userRateLimiterService.acquirePermission("default", "user2@payflow");

		userRateLimiterService.clear();

		RateLimiter limiter1 = userRateLimiterService.getRateLimiterForUser("default", "user1@payflow");
		assertThat(limiter1.getMetrics().getAvailablePermissions()).isEqualTo(10);
	}
}
