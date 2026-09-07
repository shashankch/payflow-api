package com.payflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

@ExtendWith(MockitoExtension.class)
class RedissonDistributedLockServiceTest {

	@Mock
	private RedissonClient redissonClient;

	@Mock
	private RLock rLock;

	private RedissonDistributedLockService lockService;

	@BeforeEach
	void setUp() {
		lockService = new RedissonDistributedLockService(redissonClient);
	}

	@Test
	@DisplayName("Should successfully acquire lock when Redisson rLock acquires within wait time")
	void shouldAcquireLock_whenRedissonSucceeds() throws InterruptedException {
		String key = "payflow:lock:transfer:123";
		given(redissonClient.getLock(key)).willReturn(rLock);
		given(rLock.tryLock(1000L, 5000L, TimeUnit.MILLISECONDS)).willReturn(true);

		boolean acquired = lockService.tryLock(key, Duration.ofSeconds(1), Duration.ofSeconds(5));

		assertThat(acquired).isTrue();
		verify(rLock).tryLock(1000L, 5000L, TimeUnit.MILLISECONDS);
	}

	@Test
	@DisplayName("Should return false when Redisson rLock fails to acquire within wait time")
	void shouldReturnFalse_whenRedissonFailsToAcquire() throws InterruptedException {
		String key = "payflow:lock:transfer:456";
		given(redissonClient.getLock(key)).willReturn(rLock);
		given(rLock.tryLock(0L, 10000L, TimeUnit.MILLISECONDS)).willReturn(false);

		boolean acquired = lockService.tryLock(key, Duration.ofSeconds(10));

		assertThat(acquired).isFalse();
	}

	@Test
	@DisplayName("Should handle InterruptedException and restore interrupt flag")
	void shouldHandleInterruptedException_andRestoreFlag() throws InterruptedException {
		String key = "payflow:lock:transfer:interrupted";
		given(redissonClient.getLock(key)).willReturn(rLock);
		given(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
				.willThrow(new InterruptedException("Thread interrupted"));

		boolean acquired = lockService.tryLock(key, Duration.ofSeconds(1), Duration.ofSeconds(5));

		assertThat(acquired).isFalse();
		assertThat(Thread.currentThread().isInterrupted()).isTrue();
		// Clean up thread interrupted status
		Thread.interrupted();
	}

	@Test
	@DisplayName("Should unlock RLock when held by current thread")
	void shouldUnlock_whenHeldByCurrentThread() {
		String key = "payflow:lock:transfer:held";
		given(redissonClient.getLock(key)).willReturn(rLock);
		given(rLock.isHeldByCurrentThread()).willReturn(true);

		lockService.unlock(key);

		verify(rLock).unlock();
	}

	@Test
	@DisplayName("Should skip unlocking RLock when not held by current thread")
	void shouldSkipUnlock_whenNotHeldByCurrentThread() {
		String key = "payflow:lock:transfer:not-held";
		given(redissonClient.getLock(key)).willReturn(rLock);
		given(rLock.isHeldByCurrentThread()).willReturn(false);

		lockService.unlock(key);

		verify(rLock, never()).unlock();
	}

	@Test
	@DisplayName("Should swallow exceptions safely during unlock")
	void shouldSwallowException_duringUnlock() {
		String key = "payflow:lock:transfer:error";
		given(redissonClient.getLock(key)).willReturn(rLock);
		given(rLock.isHeldByCurrentThread()).willReturn(true);
		doThrow(new RuntimeException("Redis connection lost")).when(rLock).unlock();

		// Must not propagate exception
		lockService.unlock(key);

		verify(rLock).unlock();
	}

	@Test
	@DisplayName("Should return isLocked from Redisson RLock")
	void shouldReturnIsLocked_fromRedisson() {
		String key = "payflow:lock:transfer:check";
		given(redissonClient.getLock(key)).willReturn(rLock);
		given(rLock.isLocked()).willReturn(true);

		assertThat(lockService.isLocked(key)).isTrue();
	}
}
