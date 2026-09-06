package com.payflow.resilience;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to apply per-user / per-client rate limiting to a method or class.
 * The rate limiter instance is resolved dynamically using the user's partition
 * key and the configured base template in Resilience4j.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PerUserRateLimiter {

	/**
	 * Name of the base rate limiter configuration defined in application.yml.
	 * Defaults to 'transferLimiter'.
	 */
	String name() default "transferLimiter";

	/**
	 * Optional fallback method name to invoke when rate limit is exceeded.
	 */
	String fallbackMethod() default "";
}
