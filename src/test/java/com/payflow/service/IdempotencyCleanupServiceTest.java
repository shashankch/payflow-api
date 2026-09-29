package com.payflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.payflow.repository.IdempotencyRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotencyCleanupService Unit Tests")
class IdempotencyCleanupServiceTest {

	@Mock
	private IdempotencyRepository idempotencyRepository;

	private IdempotencyCleanupService cleanupService;

	@BeforeEach
	void setUp() {
		cleanupService = new IdempotencyCleanupService(idempotencyRepository, 24L);
	}

	@Test
	@DisplayName("Should purge records older than TTL cutoff")
	void shouldPurgeRecordsOlderThanCutoff() {
		given(idempotencyRepository.deleteRecordsOlderThan(any(Instant.class))).willReturn(5);

		int deleted = cleanupService.purgeExpiredRecords();

		assertThat(deleted).isEqualTo(5);
		verify(idempotencyRepository).deleteRecordsOlderThan(any(Instant.class));
	}

	@Test
	@DisplayName("Should return 0 when no expired records found")
	void shouldReturnZeroWhenNoExpiredRecordsFound() {
		given(idempotencyRepository.deleteRecordsOlderThan(any(Instant.class))).willReturn(0);

		int deleted = cleanupService.purgeExpiredRecords();

		assertThat(deleted).isZero();
		verify(idempotencyRepository).deleteRecordsOlderThan(any(Instant.class));
	}
}
