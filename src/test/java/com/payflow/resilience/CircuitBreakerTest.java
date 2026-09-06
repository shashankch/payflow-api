package com.payflow.resilience;

import java.io.IOException;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import com.payflow.exception.InvalidUpiException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CircuitBreakerTest {

	private CircuitBreaker circuitBreaker;

	@BeforeEach
	void setUp() {
		CircuitBreakerConfig config = CircuitBreakerConfig.custom()
				.slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED).slidingWindowSize(10)
				.minimumNumberOfCalls(5).failureRateThreshold(50.0f).waitDurationInOpenState(Duration.ofMillis(200))
				.permittedNumberOfCallsInHalfOpenState(2).automaticTransitionFromOpenToHalfOpenEnabled(true)
				.recordExceptions(RestClientException.class, IOException.class)
				.ignoreExceptions(InvalidUpiException.class).build();

		CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
		circuitBreaker = registry.circuitBreaker("upiValidation");
	}

	@Test
	@DisplayName("Should start in CLOSED state")
	void shouldStartInClosedState() {
		assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
	}

	@Test
	@DisplayName("Should transition from CLOSED to OPEN after failure rate threshold is exceeded")
	void shouldTransitionToOpenAfterFailureThreshold() {
		assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

		// Record 5 calls with >50% failure (3 failures, 2 successes)
		circuitBreaker.onError(0, java.util.concurrent.TimeUnit.MILLISECONDS,
				new RestClientException("Connection refused"));
		circuitBreaker.onError(0, java.util.concurrent.TimeUnit.MILLISECONDS,
				new RestClientException("Connection timeout"));
		circuitBreaker.onError(0, java.util.concurrent.TimeUnit.MILLISECONDS, new IOException("Network reset"));
		circuitBreaker.onSuccess(0, java.util.concurrent.TimeUnit.MILLISECONDS);
		circuitBreaker.onSuccess(0, java.util.concurrent.TimeUnit.MILLISECONDS);

		// 3/5 = 60% failures >= 50% threshold -> trips to OPEN
		assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
	}

	@Test
	@DisplayName("Should throw CallNotPermittedException when circuit is in OPEN state")
	void shouldThrowCallNotPermittedExceptionWhenOpen() {
		circuitBreaker.transitionToOpenState();

		assertThatThrownBy(() -> circuitBreaker.executeSupplier(() -> "DATA"))
				.isInstanceOf(CallNotPermittedException.class);
	}

	@Test
	@DisplayName("Should transition from OPEN to HALF_OPEN after wait duration expires")
	void shouldTransitionFromOpenToHalfOpenAfterWaitDuration() throws InterruptedException {
		circuitBreaker.transitionToOpenState();
		assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

		// Wait for waitDurationInOpenState (200ms)
		Thread.sleep(250);

		// Trigger check or call
		circuitBreaker.tryAcquirePermission();
		assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
	}

	@Test
	@DisplayName("Should transition from HALF_OPEN back to CLOSED when calls succeed")
	void shouldTransitionFromHalfOpenToClosedOnSuccess() {
		circuitBreaker.transitionToOpenState();
		circuitBreaker.transitionToHalfOpenState();
		assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

		circuitBreaker.onSuccess(0, java.util.concurrent.TimeUnit.MILLISECONDS);
		circuitBreaker.onSuccess(0, java.util.concurrent.TimeUnit.MILLISECONDS);

		assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
	}

	@Test
	@DisplayName("Should not count ignored exceptions towards failure rate")
	void shouldNotCountIgnoredExceptionsAsFailures() {
		assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

		// Record 5 InvalidUpiException (422 business validation errors)
		for (int i = 0; i < 5; i++) {
			circuitBreaker.onError(0, java.util.concurrent.TimeUnit.MILLISECONDS,
					new InvalidUpiException("Invalid UPI: test@upi"));
		}

		// State should remain CLOSED because InvalidUpiException is ignored
		assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
		assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(0);
	}
}
