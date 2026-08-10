package com.payflow.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.payflow.dto.response.TransactionResponse;
import com.payflow.entity.Transaction;
import com.payflow.entity.TransactionStatus;
import com.payflow.entity.TransactionType;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionMapperTest {

	private final TransactionMapper transactionMapper = Mappers.getMapper(TransactionMapper.class);

	@Test
	@DisplayName("Should correctly map Transaction entity to TransactionResponse record")
	void shouldMapTransactionEntityToTransactionResponse() {
		Instant now = Instant.now();
		UUID refId = UUID.randomUUID();
		Transaction transaction = Transaction.builder().transactionId(500L).referenceId(refId).senderUpiId("alice@upi")
				.receiverUpiId("bob@upi").amount(new BigDecimal("100.00")).status(TransactionStatus.COMPLETED)
				.type(TransactionType.TRANSFER).note("Dinner share").createdAt(now).build();

		TransactionResponse response = transactionMapper.toResponse(transaction);

		assertThat(response).isNotNull();
		assertThat(response.transactionId()).isEqualTo(500L);
		assertThat(response.referenceId()).isEqualTo(refId);
		assertThat(response.senderUpiId()).isEqualTo("alice@upi");
		assertThat(response.receiverUpiId()).isEqualTo("bob@upi");
		assertThat(response.amount()).isEqualTo(new BigDecimal("100.00"));
		assertThat(response.status()).isEqualTo(TransactionStatus.COMPLETED);
		assertThat(response.type()).isEqualTo(TransactionType.TRANSFER);
		assertThat(response.note()).isEqualTo("Dinner share");
		assertThat(response.createdAt()).isEqualTo(now);
	}
}
