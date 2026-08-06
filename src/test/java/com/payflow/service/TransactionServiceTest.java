package com.payflow.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.payflow.dto.request.TransferMoneyRequest;
import com.payflow.entity.Transaction;
import com.payflow.entity.TransactionStatus;
import com.payflow.entity.TransactionType;
import com.payflow.entity.User;
import com.payflow.exception.InsufficientBalanceException;
import com.payflow.exception.SelfTransferException;
import com.payflow.exception.UserNotFoundException;
import com.payflow.repository.TransactionRepository;
import com.payflow.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

	@Mock
	private TransactionRepository transactionRepository;

	@Mock
	private UserRepository userRepository;

	@InjectMocks
	private TransactionService transactionService;

	private User sender;
	private User receiver;

	@BeforeEach
	void setUp() {
		sender = User.builder().userId(1L).referenceId(UUID.randomUUID()).name("Alice").upiId("alice@upi")
				.balance(new BigDecimal("1000.00")).phoneNumber("9876543210").build();

		receiver = User.builder().userId(2L).referenceId(UUID.randomUUID()).name("Bob").upiId("bob@upi")
				.balance(new BigDecimal("500.00")).phoneNumber("9876543211").build();
	}

	@Test
	@DisplayName("Should send money successfully when sender has sufficient balance")
	void shouldSendMoneySuccessfully_whenRequestIsValid() {
		TransferMoneyRequest request = TransferMoneyRequest.builder().senderUpiId("alice@upi").receiverUpiId("bob@upi")
				.amount(new BigDecimal("200.00")).note("Dinner").build();

		given(userRepository.findByUpiId("alice@upi")).willReturn(Optional.of(sender));
		given(userRepository.findByUpiId("bob@upi")).willReturn(Optional.of(receiver));
		given(transactionRepository.save(any(Transaction.class))).willAnswer(inv -> inv.getArgument(0));

		Transaction tx = transactionService.sendMoney(request);

		assertThat(tx).isNotNull();
		assertThat(sender.getBalance()).isEqualTo(new BigDecimal("800.00"));
		assertThat(receiver.getBalance()).isEqualTo(new BigDecimal("700.00"));
		assertThat(tx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
		assertThat(tx.getType()).isEqualTo(TransactionType.TRANSFER);
	}

	@Test
	@DisplayName("Should throw SelfTransferException when sender and receiver UPI IDs match")
	void shouldThrowSelfTransferException_whenSenderAndReceiverAreSame() {
		TransferMoneyRequest request = TransferMoneyRequest.builder().senderUpiId("alice@upi")
				.receiverUpiId("ALICE@upi").amount(new BigDecimal("100.00")).build();

		assertThrows(SelfTransferException.class, () -> transactionService.sendMoney(request));
	}

	@Test
	@DisplayName("Should throw UserNotFoundException when sender is not found")
	void shouldThrowUserNotFoundException_whenSenderDoesNotExist() {
		TransferMoneyRequest request = TransferMoneyRequest.builder().senderUpiId("missing@upi")
				.receiverUpiId("bob@upi").amount(new BigDecimal("100.00")).build();

		given(userRepository.findByUpiId("missing@upi")).willReturn(Optional.empty());

		assertThrows(UserNotFoundException.class, () -> transactionService.sendMoney(request));
	}

	@Test
	@DisplayName("Should throw UserNotFoundException when receiver is not found")
	void shouldThrowUserNotFoundException_whenReceiverDoesNotExist() {
		TransferMoneyRequest request = TransferMoneyRequest.builder().senderUpiId("alice@upi")
				.receiverUpiId("missing@upi").amount(new BigDecimal("100.00")).build();

		given(userRepository.findByUpiId("alice@upi")).willReturn(Optional.of(sender));
		given(userRepository.findByUpiId("missing@upi")).willReturn(Optional.empty());

		assertThrows(UserNotFoundException.class, () -> transactionService.sendMoney(request));
	}

	@Test
	@DisplayName("Should throw InsufficientBalanceException when sender has low balance")
	void shouldThrowInsufficientBalanceException_whenSenderHasLowBalance() {
		TransferMoneyRequest request = TransferMoneyRequest.builder().senderUpiId("alice@upi").receiverUpiId("bob@upi")
				.amount(new BigDecimal("1500.00")).build();

		given(userRepository.findByUpiId("alice@upi")).willReturn(Optional.of(sender));
		given(userRepository.findByUpiId("bob@upi")).willReturn(Optional.of(receiver));

		assertThrows(InsufficientBalanceException.class, () -> transactionService.sendMoney(request));
	}

	@Test
	@DisplayName("Should return transaction by reference ID when found")
	void shouldGetTransactionByReferenceId() {
		UUID refId = UUID.randomUUID();
		Transaction tx = Transaction.builder().transactionId(10L).referenceId(refId).senderUpiId("alice@upi")
				.receiverUpiId("bob@upi").amount(new BigDecimal("100.00")).status(TransactionStatus.COMPLETED)
				.type(TransactionType.TRANSFER).createdAt(Instant.now()).build();

		given(transactionRepository.findByReferenceId(refId)).willReturn(Optional.of(tx));

		Optional<Transaction> result = transactionService.getTransactionByReferenceId(refId);

		assertThat(result).isPresent();
		assertThat(result.get().getReferenceId()).isEqualTo(refId);
	}

	@Test
	@DisplayName("Should return paginated transactions for a user")
	void shouldGetUserTransactionsPaginated() {
		Pageable pageable = PageRequest.of(0, 10);
		Transaction tx = Transaction.builder().transactionId(1L).referenceId(UUID.randomUUID()).senderUpiId("alice@upi")
				.receiverUpiId("bob@upi").amount(new BigDecimal("50.00")).status(TransactionStatus.COMPLETED)
				.type(TransactionType.TRANSFER).createdAt(Instant.now()).build();

		Page<Transaction> page = new PageImpl<>(List.of(tx), pageable, 1);
		given(transactionRepository.findBySenderUpiIdOrReceiverUpiId("alice@upi", "alice@upi", pageable))
				.willReturn(page);

		Page<Transaction> result = transactionService.getUserTransactions("alice@upi", pageable);

		assertThat(result).isNotNull();
		assertThat(result.getContent()).hasSize(1);
	}
}
