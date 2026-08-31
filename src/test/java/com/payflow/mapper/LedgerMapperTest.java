package com.payflow.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.payflow.dto.response.LedgerEntryResponse;
import com.payflow.entity.BalanceLedgerEntry;
import com.payflow.entity.LedgerEntryType;
import com.payflow.entity.Transaction;
import com.payflow.entity.User;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerMapperTest {

	private final LedgerMapper ledgerMapper = new LedgerMapperImpl();

	@Test
	@DisplayName("Should correctly map BalanceLedgerEntry entity to LedgerEntryResponse record")
	void shouldMapBalanceLedgerEntryToLedgerEntryResponse() {
		UUID userRefId = UUID.randomUUID();
		UUID txRefId = UUID.randomUUID();
		Instant now = Instant.now();

		User user = User.builder().userId(1L).referenceId(userRefId).build();
		Transaction tx = Transaction.builder().transactionId(10L).referenceId(txRefId).build();

		BalanceLedgerEntry entry = BalanceLedgerEntry.builder().ledgerId(50L).user(user).transaction(tx)
				.entryType(LedgerEntryType.CREDIT).amount(new BigDecimal("100.00"))
				.balanceBefore(new BigDecimal("300.00")).balanceAfter(new BigDecimal("400.00")).createdAt(now).build();

		LedgerEntryResponse response = ledgerMapper.toResponse(entry);

		assertThat(response).isNotNull();
		assertThat(response.ledgerId()).isEqualTo(50L);
		assertThat(response.userReferenceId()).isEqualTo(userRefId);
		assertThat(response.transactionReferenceId()).isEqualTo(txRefId);
		assertThat(response.entryType()).isEqualTo(LedgerEntryType.CREDIT);
		assertThat(response.amount()).isEqualTo(new BigDecimal("100.00"));
		assertThat(response.balanceBefore()).isEqualTo(new BigDecimal("300.00"));
		assertThat(response.balanceAfter()).isEqualTo(new BigDecimal("400.00"));
		assertThat(response.createdAt()).isEqualTo(now);
	}
}
