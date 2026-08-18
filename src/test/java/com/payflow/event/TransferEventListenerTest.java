package com.payflow.event;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.payflow.entity.TransactionStatus;

class TransferEventListenerTest {

	private final TransferEventListener listener = new TransferEventListener();

	@Test
	@DisplayName("Should successfully consume and process TransferCompletedEvent")
	void shouldProcessTransferCompletedEvent_withoutExceptions() {
		TransferCompletedEvent event = new TransferCompletedEvent(UUID.randomUUID(), "alice@payflow", "bob@payflow",
				new BigDecimal("150.00"), TransactionStatus.COMPLETED, new BigDecimal("850.00"),
				new BigDecimal("650.00"), Instant.now());

		assertThatCode(() -> listener.onTransferCompleted(event)).doesNotThrowAnyException();
	}
}
