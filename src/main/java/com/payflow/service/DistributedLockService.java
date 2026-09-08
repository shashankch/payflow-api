package com.payflow.service;

import java.time.Duration;

/**
 * Contract for distributed lock management across application instances.
 */
public interface DistributedLockService {

	/**
	 * Attempts to acquire a distributed lock with explicit wait and lease
	 * durations.
	 *
	 * @param lockKey
	 *            the unique lock identifier
	 * @param waitTime
	 *            the maximum duration to wait for lock acquisition
	 * @param leaseTime
	 *            the duration after which the lock automatically expires
	 * @return true if the lock was successfully acquired, false otherwise
	 */
	boolean tryLock(String lockKey, Duration waitTime, Duration leaseTime);

	/**
	 * Attempts to acquire a distributed lock immediately without waiting
	 * (fail-fast).
	 *
	 * @param lockKey
	 *            the unique lock identifier
	 * @param leaseTime
	 *            the duration after which the lock automatically expires
	 * @return true if the lock was successfully acquired, false otherwise
	 */
	default boolean tryLock(String lockKey, Duration leaseTime) {
		return tryLock(lockKey, Duration.ZERO, leaseTime);
	}

	/**
	 * Releases the distributed lock identified by the given key.
	 *
	 * @param lockKey
	 *            the unique lock identifier
	 */
	void unlock(String lockKey);

	/**
	 * Checks whether the distributed lock is currently held.
	 *
	 * @param lockKey
	 *            the unique lock identifier
	 * @return true if the lock is held, false otherwise
	 */
	boolean isLocked(String lockKey);
}
