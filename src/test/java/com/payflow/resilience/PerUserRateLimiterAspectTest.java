package com.payflow.resilience;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.payflow.config.RateLimiterKeyResolver;

import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerUserRateLimiterAspectTest {

	@Mock
	private RateLimiterKeyResolver keyResolver;

	@Mock
	private ProceedingJoinPoint joinPoint;

	private RateLimiterRegistry registry;
	private UserRateLimiterService userRateLimiterService;
	private PerUserRateLimiterAspect aspect;

	@BeforeEach
	void setUp() {
		RateLimiterConfig config = RateLimiterConfig.custom().limitForPeriod(1)
				.limitRefreshPeriod(java.time.Duration.ofSeconds(10)).timeoutDuration(java.time.Duration.ZERO).build();
		registry = RateLimiterRegistry.of(config);
		registry.addConfiguration("transferLimiter", config);
		userRateLimiterService = new UserRateLimiterService(registry);
		aspect = new PerUserRateLimiterAspect(userRateLimiterService, keyResolver);
	}

	@Test
	@DisplayName("Should proceed with method execution when permission is available")
	void shouldProceedWhenPermissionAvailable() throws Throwable {
		when(keyResolver.resolveKey()).thenReturn("alice@payflow");
		when(joinPoint.proceed()).thenReturn("SUCCESS");

		PerUserRateLimiter annotation = mock(PerUserRateLimiter.class);
		when(annotation.name()).thenReturn("transferLimiter");

		Object result = aspect.enforceRateLimit(joinPoint, annotation);

		assertThat(result).isEqualTo("SUCCESS");
		verify(joinPoint).proceed();
	}

	@Test
	@DisplayName("Should throw RequestNotPermitted when rate limit is exceeded without fallback")
	void shouldThrowWhenLimitExceeded() throws Throwable {
		when(keyResolver.resolveKey()).thenReturn("alice@payflow");
		when(joinPoint.proceed()).thenReturn("SUCCESS");

		MethodSignature signature = mock(MethodSignature.class);
		when(signature.getName()).thenReturn("sendMoney");
		when(joinPoint.getSignature()).thenReturn(signature);
		when(joinPoint.getTarget()).thenReturn(new Object());

		PerUserRateLimiter annotation = mock(PerUserRateLimiter.class);
		when(annotation.name()).thenReturn("transferLimiter");
		when(annotation.fallbackMethod()).thenReturn("");

		// First call succeeds
		aspect.enforceRateLimit(joinPoint, annotation);

		// Second call within 10s period exceeds limit
		assertThatThrownBy(() -> aspect.enforceRateLimit(joinPoint, annotation))
				.isInstanceOf(RequestNotPermitted.class);
	}

	@Test
	@DisplayName("Should invoke fallback method when configured and limit is exceeded")
	void shouldInvokeFallbackWhenConfigured() throws Throwable {
		when(keyResolver.resolveKey()).thenReturn("alice@payflow");
		when(joinPoint.proceed()).thenReturn("SUCCESS");

		SampleService target = new SampleService();
		when(joinPoint.getTarget()).thenReturn(target);
		when(joinPoint.getArgs()).thenReturn(new Object[]{"testArg"});

		MethodSignature signature = mock(MethodSignature.class);
		when(signature.getName()).thenReturn("doSomething");
		when(signature.getParameterTypes()).thenReturn(new Class<?>[]{String.class});
		when(joinPoint.getSignature()).thenReturn(signature);

		PerUserRateLimiter annotation = mock(PerUserRateLimiter.class);
		when(annotation.name()).thenReturn("transferLimiter");
		when(annotation.fallbackMethod()).thenReturn("fallback");

		// 1st call uses up quota
		aspect.enforceRateLimit(joinPoint, annotation);

		// 2nd call invokes fallback
		Object result = aspect.enforceRateLimit(joinPoint, annotation);
		assertThat(result).isEqualTo("FALLBACK_CALLED: testArg");
	}

	static class SampleService {
		public String fallback(String arg, Throwable t) {
			return "FALLBACK_CALLED: " + arg;
		}
	}
}
