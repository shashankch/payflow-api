package com.payflow.service;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * In-memory No-Op fallback implementation of DistributedLockService for
 * non-production profiles (local, test, prod-light).
 */
@Service
@Profile("!prod")
public class NoOpDistributedLockService implements DistributedLockService {

	private static final Logger LOG = LoggerFactory.getLogger(NoOpDistributedLockService.class);

	@Override
	public boolean tryLock(String lockKey, Duration waitTime, Duration leaseTime) {
		LOG.debug("NoOp lock acquired: key={}, wait={}, lease={}", lockKey, waitTime, leaseTime);
		return true;
	}

	@Override
	public void unlock(String lockKey) {
		LOG.debug("NoOp distributed lock released: key={}", lockKey);
	}

	@Override
	public boolean isLocked(String lockKey) {
		return false;
	}
}
