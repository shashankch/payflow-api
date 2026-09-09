package com.payflow.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.events.Externalized;

import com.payflow.entity.TransactionStatus;

class TransferCompletedEventTest {

	@Test
	@DisplayName("Should be annotated with @Externalized routing to payflow.transfers partitioned by senderUpi")
	void shouldHaveExternalizedAnnotationWithCorrectRouting() {
		Externalized externalized = TransferCompletedEvent.class.getAnnotation(Externalized.class);

		assertThat(externalized).isNotNull();
		assertThat(externalized.value()).isEqualTo("payflow.transfers::#{senderUpi()}");
	}

	@Test
	@DisplayName("Should correctly instantiate and expose all record fields")
	void shouldInstantiateAndExposeFields() {
		UUID refId = UUID.randomUUID();
		Instant now = Instant.now();
		TransferCompletedEvent event = new TransferCompletedEvent(refId, "sender@payflow", "receiver@payflow",
				new BigDecimal("100.00"), TransactionStatus.COMPLETED, new BigDecimal("900.00"),
				new BigDecimal("600.00"), now);

		assertThat(event.referenceId()).isEqualTo(refId);
		assertThat(event.senderUpi()).isEqualTo("sender@payflow");
		assertThat(event.receiverUpi()).isEqualTo("receiver@payflow");
		assertThat(event.amount()).isEqualByComparingTo(new BigDecimal("100.00"));
		assertThat(event.status()).isEqualTo(TransactionStatus.COMPLETED);
		assertThat(event.senderAfter()).isEqualByComparingTo(new BigDecimal("900.00"));
		assertThat(event.receiverAfter()).isEqualByComparingTo(new BigDecimal("600.00"));
		assertThat(event.timestamp()).isEqualTo(now);
	}
}
