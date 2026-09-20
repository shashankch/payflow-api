package com.payflow.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
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

import com.payflow.entity.Transaction;
import com.payflow.entity.TransactionStatus;
import com.payflow.entity.TransactionType;
import com.payflow.entity.User;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TransactionRepositoryTest {

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private TransactionRepository transactionRepository;

	private User sender;
	private User receiver;
	private Transaction transaction;
	private UUID sampleRefId;

	@BeforeEach
	void setUp() {
		sender = User.builder().referenceId(UUID.randomUUID()).name("Alice Sender").upiId("alice@payflow")
				.phoneNumber("9876543210").balance(new BigDecimal("1000.0000")).version(0L).createdAt(Instant.now())
				.updatedAt(Instant.now()).build();
		sender = entityManager.persistAndFlush(sender);

		receiver = User.builder().referenceId(UUID.randomUUID()).name("Bob Receiver").upiId("bob@payflow")
				.phoneNumber("9876543211").balance(new BigDecimal("500.0000")).version(0L).createdAt(Instant.now())
				.updatedAt(Instant.now()).build();
		receiver = entityManager.persistAndFlush(receiver);

		sampleRefId = UUID.randomUUID();
		transaction = Transaction.builder().referenceId(sampleRefId).sender(sender).receiver(receiver)
				.senderUpiId("alice@payflow").receiverUpiId("bob@payflow").amount(new BigDecimal("250.0000"))
				.status(TransactionStatus.COMPLETED).type(TransactionType.TRANSFER).note("Dinner split")
				.createdAt(Instant.now()).build();
		transaction = entityManager.persistAndFlush(transaction);
	}

	@Test
	@DisplayName("Should find transaction by reference UUID with sender and receiver eagerly loaded")
	void shouldFindByReferenceId() {
		Optional<Transaction> found = transactionRepository.findByReferenceId(sampleRefId);

		assertThat(found).isPresent();
		assertThat(found.get().getReferenceId()).isEqualTo(sampleRefId);
		assertThat(found.get().getSender().getUpiId()).isEqualTo("alice@payflow");
		assertThat(found.get().getReceiver().getUpiId()).isEqualTo("bob@payflow");
		assertThat(found.get().getAmount()).isEqualByComparingTo("250.0000");
		assertThat(found.get().getStatus()).isEqualTo(TransactionStatus.COMPLETED);
	}

	@Test
	@DisplayName("Should return empty optional when reference ID does not exist")
	void shouldReturnEmptyForNonExistentReferenceId() {
		Optional<Transaction> found = transactionRepository.findByReferenceId(UUID.randomUUID());

		assertThat(found).isEmpty();
	}

	@Test
	@DisplayName("Should find transactions by sender or receiver UPI ID with pagination")
	void shouldFindBySenderUpiIdOrReceiverUpiId() {
		Page<Transaction> page = transactionRepository.findBySenderUpiIdOrReceiverUpiId("alice@payflow",
				"alice@payflow", PageRequest.of(0, 10));

		assertThat(page.getTotalElements()).isEqualTo(1);
		assertThat(page.getContent().get(0).getReferenceId()).isEqualTo(sampleRefId);

		Page<Transaction> receiverPage = transactionRepository.findBySenderUpiIdOrReceiverUpiId("bob@payflow",
				"bob@payflow", PageRequest.of(0, 10));
		assertThat(receiverPage.getTotalElements()).isEqualTo(1);

		Page<Transaction> unrelatedPage = transactionRepository.findBySenderUpiIdOrReceiverUpiId("unknown@payflow",
				"unknown@payflow", PageRequest.of(0, 10));
		assertThat(unrelatedPage.getTotalElements()).isZero();
	}
}
