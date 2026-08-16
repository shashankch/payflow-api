package com.payflow.service;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.payflow.repository.IdempotencyRepository;

@Service
public class IdempotencyCleanupService {

	private static final Logger LOG = LoggerFactory.getLogger(IdempotencyCleanupService.class);

	private final IdempotencyRepository idempotencyRepository;
	private final long ttlHours;

	public IdempotencyCleanupService(IdempotencyRepository idempotencyRepository,
			@Value("${payflow.idempotency.ttl-hours:24}") long ttlHours) {
		this.idempotencyRepository = idempotencyRepository;
		this.ttlHours = ttlHours;
	}

	@Scheduled(cron = "${payflow.idempotency.cleanup-cron:0 0 * * * *}")
	@Transactional
	public int purgeExpiredRecords() {
		Instant cutoff = Instant.now().minus(Duration.ofHours(ttlHours));
		int deletedCount = idempotencyRepository.deleteRecordsOlderThan(cutoff);
		if (deletedCount > 0) {
			LOG.info("Purged {} expired idempotency records older than {}", deletedCount, cutoff);
		}
		return deletedCount;
	}
}
