package com.payflow.service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Production implementation of DistributedLockService backed by Redisson and
 * Redis.
 */
@Service
@Profile("prod")
public class RedissonDistributedLockService implements DistributedLockService {

	private static final Logger LOG = LoggerFactory.getLogger(RedissonDistributedLockService.class);

	private final RedissonClient redissonClient;

	public RedissonDistributedLockService(RedissonClient redissonClient) {
		this.redissonClient = redissonClient;
	}

	@Override
	public boolean tryLock(String lockKey, Duration waitTime, Duration leaseTime) {
		RLock lock = redissonClient.getLock(lockKey);
		try {
			long waitMs = waitTime.toMillis();
			long leaseMs = leaseTime.toMillis();
			boolean acquired = lock.tryLock(waitMs, leaseMs, TimeUnit.MILLISECONDS);
			if (acquired) {
				LOG.debug("Acquired distributed lock: key={}, leaseTime={}", lockKey, leaseTime);
			} else {
				LOG.warn("Failed to acquire distributed lock: key={}, waitTime={}", lockKey, waitTime);
			}
			return acquired;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			LOG.error("Interrupted while acquiring distributed lock for key={}", lockKey, e);
			return false;
		}
	}

	@Override
	public void unlock(String lockKey) {
		RLock lock = redissonClient.getLock(lockKey);
		try {
			if (lock.isHeldByCurrentThread()) {
				lock.unlock();
				LOG.debug("Released distributed lock: key={}", lockKey);
			} else {
				LOG.debug("Lock not held by current thread, skipping unlock: key={}", lockKey);
			}
		} catch (Exception e) {
			LOG.warn("Exception while releasing distributed lock for key={}: {}", lockKey, e.getMessage());
		}
	}

	@Override
	public boolean isLocked(String lockKey) {
		return redissonClient.getLock(lockKey).isLocked();
	}
}
