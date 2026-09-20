package com.payflow.service;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.modulith.events.CompletedEventPublications;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxCleanupServiceTest {

	@Mock
	private CompletedEventPublications completedEventPublications;

	@Test
	@DisplayName("Should invoke deletePublicationsOlderThan with configured duration")
	void shouldInvokeDeletePublicationsOlderThan() {
		OutboxCleanupService service = new OutboxCleanupService(completedEventPublications, 7);

		assertDoesNotThrow(service::purgeCompletedOutboxEvents);

		verify(completedEventPublications).deletePublicationsOlderThan(eq(Duration.ofDays(7)));
	}

	@Test
	@DisplayName("Should handle exceptions gracefully without propagating")
	void shouldHandleExceptionsGracefully() {
		doThrow(new RuntimeException("DB Connection Timeout")).when(completedEventPublications)
				.deletePublicationsOlderThan(eq(Duration.ofDays(7)));

		OutboxCleanupService service = new OutboxCleanupService(completedEventPublications, 7);

		assertDoesNotThrow(service::purgeCompletedOutboxEvents);
	}

	@Test
	@DisplayName("Should safely no-op if completedEventPublications is null")
	void shouldNoOpWhenPublicationsBeanIsNull() {
		OutboxCleanupService service = new OutboxCleanupService(null, 7);

		assertDoesNotThrow(service::purgeCompletedOutboxEvents);
	}
}
