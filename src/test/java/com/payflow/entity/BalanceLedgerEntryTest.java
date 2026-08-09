package com.payflow.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BalanceLedgerEntryTest {

	@Test
	@DisplayName("Should correctly instantiate BalanceLedgerEntry via Builder")
	void shouldInstantiateBalanceLedgerEntry_viaBuilder() {
		User user = User.builder().userId(1L).referenceId(UUID.randomUUID()).build();
		Transaction tx = Transaction.builder().transactionId(10L).referenceId(UUID.randomUUID()).build();
		Instant now = Instant.now();

		BalanceLedgerEntry entry = BalanceLedgerEntry.builder().ledgerId(100L).user(user).transaction(tx)
				.entryType(LedgerEntryType.DEBIT).amount(new BigDecimal("50.00"))
				.balanceBefore(new BigDecimal("200.00")).balanceAfter(new BigDecimal("150.00")).createdAt(now).build();

		assertThat(entry).isNotNull();
		assertThat(entry.getLedgerId()).isEqualTo(100L);
		assertThat(entry.getUser()).isEqualTo(user);
		assertThat(entry.getTransaction()).isEqualTo(tx);
		assertThat(entry.getEntryType()).isEqualTo(LedgerEntryType.DEBIT);
		assertThat(entry.getAmount()).isEqualTo(new BigDecimal("50.00"));
		assertThat(entry.getBalanceBefore()).isEqualTo(new BigDecimal("200.00"));
		assertThat(entry.getBalanceAfter()).isEqualTo(new BigDecimal("150.00"));
		assertThat(entry.getCreatedAt()).isEqualTo(now);
	}
}
