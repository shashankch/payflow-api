package com.payflow.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.payflow.entity.BalanceLedgerEntry;
import com.payflow.entity.LedgerEntryType;
import com.payflow.entity.Transaction;
import com.payflow.entity.TransactionStatus;
import com.payflow.entity.TransactionType;
import com.payflow.entity.User;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BalanceLedgerRepositoryTest {

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private BalanceLedgerRepository balanceLedgerRepository;

	private User sampleUser;
	private Transaction sampleTx;

	@BeforeEach
	void setUp() {
		sampleUser = entityManager.persistAndFlush(User.builder().referenceId(UUID.randomUUID()).name("Vikram Malhotra")
				.upiId("vikram@payflow").phoneNumber("9123456789").balance(new BigDecimal("300.0000")).version(0L)
				.createdAt(Instant.now()).updatedAt(Instant.now()).build());

		sampleTx = entityManager.persistAndFlush(Transaction.builder().referenceId(UUID.randomUUID()).sender(sampleUser)
				.receiver(sampleUser).senderUpiId("vikram@payflow").receiverUpiId("vikram@payflow")
				.amount(new BigDecimal("500.0000")).status(TransactionStatus.COMPLETED).type(TransactionType.TRANSFER)
				.createdAt(Instant.now()).build());
	}

	@Test
	@DisplayName("Should correctly calculate reconciled balance by sum of CREDIT minus DEBIT entries")
	void shouldCalculateReconciledBalanceByUserId() {
		// CREDIT 500
		BalanceLedgerEntry creditEntry = BalanceLedgerEntry.builder().user(sampleUser).transaction(sampleTx)
				.entryType(LedgerEntryType.CREDIT).amount(new BigDecimal("500.0000")).balanceBefore(BigDecimal.ZERO)
				.balanceAfter(new BigDecimal("500.0000")).createdAt(Instant.now()).build();
		entityManager.persistAndFlush(creditEntry);

		// DEBIT 200
		BalanceLedgerEntry debitEntry = BalanceLedgerEntry.builder().user(sampleUser).transaction(sampleTx)
				.entryType(LedgerEntryType.DEBIT).amount(new BigDecimal("200.0000"))
				.balanceBefore(new BigDecimal("500.0000")).balanceAfter(new BigDecimal("300.0000"))
				.createdAt(Instant.now().plusMillis(100)).build();
		entityManager.persistAndFlush(debitEntry);

		BigDecimal reconciled = balanceLedgerRepository.calculateReconciledBalanceByUserId(sampleUser.getUserId());

		assertThat(reconciled).isEqualByComparingTo("300.0000");
	}

	@Test
	@DisplayName("Should return zero reconciled balance when user has no ledger entries")
	void shouldReturnZeroReconciledBalance_whenNoEntriesExist() {
		BigDecimal reconciled = balanceLedgerRepository.calculateReconciledBalanceByUserId(sampleUser.getUserId());

		assertThat(reconciled).isEqualByComparingTo("0");
	}

	@Test
	@DisplayName("Should find user balance ledger entries paginated and ordered by createdAt DESC")
	void shouldFindByUserReferenceIdOrderByCreatedAtDesc() {
		BalanceLedgerEntry entry1 = BalanceLedgerEntry.builder().user(sampleUser).transaction(sampleTx)
				.entryType(LedgerEntryType.CREDIT).amount(new BigDecimal("500.0000")).balanceBefore(BigDecimal.ZERO)
				.balanceAfter(new BigDecimal("500.0000")).createdAt(Instant.now()).build();
		entityManager.persistAndFlush(entry1);

		Page<BalanceLedgerEntry> result = balanceLedgerRepository
				.findByUserReferenceIdOrderByCreatedAtDesc(sampleUser.getReferenceId(), PageRequest.of(0, 10));

		assertThat(result).isNotNull();
		assertThat(result.getTotalElements()).isEqualTo(1);
		assertThat(result.getContent().get(0).getAmount()).isEqualByComparingTo("500.0000");
	}
}
