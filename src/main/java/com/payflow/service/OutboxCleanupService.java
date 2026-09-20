package com.payflow.service;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxCleanupService {

	private static final Logger LOG = LoggerFactory.getLogger(OutboxCleanupService.class);

	private final CompletedEventPublications completedEventPublications;
	private final Duration retentionDuration;

	public OutboxCleanupService(@Autowired(required = false) CompletedEventPublications completedEventPublications,
			@Value("${payflow.outbox.retention-days:7}") long retentionDays) {
		this.completedEventPublications = completedEventPublications;
		this.retentionDuration = Duration.ofDays(retentionDays);
	}

	@Scheduled(cron = "${payflow.outbox.cleanup-cron:0 0 2 * * *}")
	@Transactional
	public void purgeCompletedOutboxEvents() {
		if (completedEventPublications == null) {
			LOG.debug("CompletedEventPublications bean not available; skipping outbox purge");
			return;
		}
		LOG.info("Starting scheduled purge of completed outbox events older than {}", retentionDuration);
		try {
			completedEventPublications.deletePublicationsOlderThan(retentionDuration);
			LOG.info("Completed outbox event purge successfully finished");
		} catch (Exception ex) {
			LOG.error("Failed to purge completed outbox events: {}", ex.getMessage(), ex);
		}
	}
}
