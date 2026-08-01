package com.payflow.entity;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class TransactionTest {

	@Test
	void shouldEnsureReferenceIdOnPrePersist() {
		Transaction transaction = Transaction.builder().senderUpiId("alice@upi").receiverUpiId("bob@upi")
				.amount(new BigDecimal("100.00")).status(TransactionStatus.INITIATED).type(TransactionType.TRANSFER)
				.build();

		transaction.ensureReferenceId();
		assertNotNull(transaction.getReferenceId());
	}
}
