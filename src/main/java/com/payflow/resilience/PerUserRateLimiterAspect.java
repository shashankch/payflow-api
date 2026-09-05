package com.payflow.resilience;

import java.lang.reflect.Method;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.payflow.config.RateLimiterKeyResolver;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;

/**
 * Spring AOP Aspect intercepting methods annotated with
 * {@link PerUserRateLimiter}. Enforces per-user rate limiting using dynamic
 * partition keys and throws {@link RequestNotPermitted} when capacity is
 * exceeded.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
public class PerUserRateLimiterAspect {

	private static final Logger LOG = LoggerFactory.getLogger(PerUserRateLimiterAspect.class);

	private final UserRateLimiterService userRateLimiterService;
	private final RateLimiterKeyResolver rateLimiterKeyResolver;

	public PerUserRateLimiterAspect(UserRateLimiterService userRateLimiterService,
			RateLimiterKeyResolver rateLimiterKeyResolver) {
		this.userRateLimiterService = userRateLimiterService;
		this.rateLimiterKeyResolver = rateLimiterKeyResolver;
	}

	@Around("@annotation(perUserRateLimiter)")
	public Object enforceRateLimit(ProceedingJoinPoint joinPoint, PerUserRateLimiter perUserRateLimiter)
			throws Throwable {
		String userKey = rateLimiterKeyResolver.resolveKey();
		String baseConfigName = perUserRateLimiter.name();

		RateLimiter rateLimiter = userRateLimiterService.getRateLimiterForUser(baseConfigName, userKey);
		boolean permissionAcquired = rateLimiter.acquirePermission();

		if (!permissionAcquired) {
			String targetClass = joinPoint.getTarget().getClass().getSimpleName();
			String targetMethod = joinPoint.getSignature().getName();
			LOG.warn("Per-user rate limit exceeded for user '{}' on method '{}.{}'", userKey, targetClass,
					targetMethod);

			String fallbackMethodName = perUserRateLimiter.fallbackMethod();
			if (fallbackMethodName != null && !fallbackMethodName.isBlank()) {
				return invokeFallback(joinPoint, fallbackMethodName,
						RequestNotPermitted.createRequestNotPermitted(rateLimiter));
			}

			throw RequestNotPermitted.createRequestNotPermitted(rateLimiter);
		}

		return joinPoint.proceed();
	}

	private Object invokeFallback(ProceedingJoinPoint joinPoint, String fallbackMethodName, Throwable cause)
			throws Throwable {
		Object target = joinPoint.getTarget();
		Object[] args = joinPoint.getArgs();
		MethodSignature signature = (MethodSignature) joinPoint.getSignature();
		Class<?>[] paramTypes = signature.getParameterTypes();

		// Try finding method with (originalArgs..., Throwable)
		Class<?>[] fallbackParamTypes = new Class<?>[paramTypes.length + 1];
		System.arraycopy(paramTypes, 0, fallbackParamTypes, 0, paramTypes.length);
		fallbackParamTypes[paramTypes.length] = Throwable.class;

		Object[] fallbackArgs = new Object[args.length + 1];
		System.arraycopy(args, 0, fallbackArgs, 0, args.length);
		fallbackArgs[args.length] = cause;

		try {
			Method fallbackMethod = target.getClass().getMethod(fallbackMethodName, fallbackParamTypes);
			return fallbackMethod.invoke(target, fallbackArgs);
		} catch (NoSuchMethodException e) {
			// Try finding method with exact cause class
			fallbackParamTypes[paramTypes.length] = cause.getClass();
			try {
				Class<?> targetClass = target.getClass();
				Method fallbackMethod = targetClass.getMethod(fallbackMethodName, fallbackParamTypes);
				return fallbackMethod.invoke(target, fallbackArgs);
			} catch (NoSuchMethodException ex) {
				LOG.error("Fallback method '{}(...)' not found on {}", fallbackMethodName,
						target.getClass().getSimpleName());
				throw cause;
			}
		}
	}
}
