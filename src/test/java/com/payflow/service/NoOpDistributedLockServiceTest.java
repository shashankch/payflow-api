package com.payflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NoOpDistributedLockServiceTest {

	private NoOpDistributedLockService lockService;

	@BeforeEach
	void setUp() {
		lockService = new NoOpDistributedLockService();
	}

	@Test
	@DisplayName("Should always successfully acquire lock in tryLock with wait and lease times")
	void shouldAcquireLock_withWaitAndLeaseTime() {
		boolean acquired = lockService.tryLock("test-key", Duration.ofSeconds(1), Duration.ofSeconds(5));
		assertThat(acquired).isTrue();
	}

	@Test
	@DisplayName("Should always successfully acquire lock in default tryLock with lease time")
	void shouldAcquireLock_withLeaseTimeOnly() {
		boolean acquired = lockService.tryLock("test-key", Duration.ofSeconds(5));
		assertThat(acquired).isTrue();
	}

	@Test
	@DisplayName("Should execute unlock without throwing exceptions")
	void shouldExecuteUnlockWithoutException() {
		assertThatCode(() -> lockService.unlock("test-key")).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("Should return false for isLocked")
	void shouldReturnFalseForIsLocked() {
		assertThat(lockService.isLocked("test-key")).isFalse();
	}
}
