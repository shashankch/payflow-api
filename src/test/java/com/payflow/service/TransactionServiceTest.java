package com.payflow.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
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
import com.payflow.exception.TransactionNotFoundException;
import com.payflow.exception.UserNotFoundException;
import com.payflow.repository.TransactionRepository;
import com.payflow.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
		sender = new User(1L, UUID.randomUUID(), "Alice Smith", "alice@payflow", new BigDecimal("500.00"), "9876543210",
				0L, null, null);
		receiver = new User(2L, UUID.randomUUID(), "Bob Jones", "bob@payflow", new BigDecimal("200.00"), "9876543211",
				0L, null, null);
	}

	@Test
	@DisplayName("Should complete transfer successfully when request is valid")
	void shouldCompleteTransfer_whenValidRequest() {
		TransferMoneyRequest request = new TransferMoneyRequest("alice@payflow", "bob@payflow",
				new BigDecimal("100.00"), "Rent payment");

		when(userRepository.findByUpiIdWithLock("alice@payflow")).thenReturn(Optional.of(sender));
		when(userRepository.findByUpiIdWithLock("bob@payflow")).thenReturn(Optional.of(receiver));
		when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Transaction result = transactionService.sendMoney(request);

		assertThat(result).isNotNull();
		assertThat(result.getAmount()).isEqualTo(new BigDecimal("100.00"));
		assertThat(result.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
		assertThat(result.getType()).isEqualTo(TransactionType.TRANSFER);
		assertThat(sender.getBalance()).isEqualTo(new BigDecimal("400.00"));
		assertThat(receiver.getBalance()).isEqualTo(new BigDecimal("300.00"));
		verify(userRepository).save(sender);
		verify(userRepository).save(receiver);
	}

	@Test
	@DisplayName("Should lock accounts in alphabetical order to prevent deadlocks when sender > receiver")
	void shouldLockAccountsInAlphabeticalOrder_whenSenderIsAlphabeticallyAfterReceiver() {
		TransferMoneyRequest request = new TransferMoneyRequest("bob@payflow", "alice@payflow", new BigDecimal("50.00"),
				"Reverse transfer");

		when(userRepository.findByUpiIdWithLock("alice@payflow")).thenReturn(Optional.of(sender));
		when(userRepository.findByUpiIdWithLock("bob@payflow")).thenReturn(Optional.of(receiver));
		when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

		transactionService.sendMoney(request);

		InOrder inOrder = inOrder(userRepository);
		inOrder.verify(userRepository).findByUpiIdWithLock("alice@payflow");
		inOrder.verify(userRepository).findByUpiIdWithLock("bob@payflow");
	}

	@Test
	@DisplayName("Should throw SelfTransferException when sender UPI equals receiver UPI")
	void shouldThrowSelfTransferException_whenSenderEqualsReceiver() {
		TransferMoneyRequest request = new TransferMoneyRequest("alice@payflow", "alice@payflow",
				new BigDecimal("50.00"), "Self transfer");

		assertThatThrownBy(() -> transactionService.sendMoney(request)).isInstanceOf(SelfTransferException.class)
				.hasMessageContaining("alice@payflow");
	}

	@Test
	@DisplayName("Should throw UserNotFoundException when sender is not found")
	void shouldThrowUserNotFoundException_whenSenderNotFound() {
		TransferMoneyRequest request = new TransferMoneyRequest("unknown@payflow", "bob@payflow",
				new BigDecimal("50.00"), "Test");

		when(userRepository.findByUpiIdWithLock("bob@payflow")).thenReturn(Optional.of(receiver));
		when(userRepository.findByUpiIdWithLock("unknown@payflow")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> transactionService.sendMoney(request)).isInstanceOf(UserNotFoundException.class)
				.hasMessageContaining("Sender not found");
	}

	@Test
	@DisplayName("Should throw UserNotFoundException when receiver is not found")
	void shouldThrowUserNotFoundException_whenReceiverNotFound() {
		TransferMoneyRequest request = new TransferMoneyRequest("alice@payflow", "unknown@payflow",
				new BigDecimal("50.00"), "Test");

		when(userRepository.findByUpiIdWithLock("alice@payflow")).thenReturn(Optional.of(sender));
		when(userRepository.findByUpiIdWithLock("unknown@payflow")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> transactionService.sendMoney(request)).isInstanceOf(UserNotFoundException.class)
				.hasMessageContaining("Receiver not found");
	}

	@Test
	@DisplayName("Should throw InsufficientBalanceException when sender balance is lower than transfer amount")
	void shouldThrowInsufficientBalanceException_whenSenderBalanceTooLow() {
		TransferMoneyRequest request = new TransferMoneyRequest("alice@payflow", "bob@payflow",
				new BigDecimal("1000.00"), "Big transfer");

		when(userRepository.findByUpiIdWithLock("alice@payflow")).thenReturn(Optional.of(sender));
		when(userRepository.findByUpiIdWithLock("bob@payflow")).thenReturn(Optional.of(receiver));

		assertThatThrownBy(() -> transactionService.sendMoney(request))
				.isInstanceOf(InsufficientBalanceException.class);
	}

	@Test
	@DisplayName("Should return transaction when reference ID exists")
	void shouldReturnTransaction_whenReferenceIdExists() {
		UUID refId = UUID.randomUUID();
		Transaction tx = Transaction.builder().referenceId(refId).sender(sender).receiver(receiver)
				.senderUpiId("alice@payflow").receiverUpiId("bob@payflow").amount(new BigDecimal("100.00"))
				.status(TransactionStatus.COMPLETED).type(TransactionType.TRANSFER).build();

		when(transactionRepository.findByReferenceId(refId)).thenReturn(Optional.of(tx));

		Transaction result = transactionService.getTransactionByReferenceId(refId);

		assertThat(result).isNotNull();
		assertThat(result.getReferenceId()).isEqualTo(refId);
	}

	@Test
	@DisplayName("Should throw TransactionNotFoundException when reference ID does not exist")
	void shouldThrowTransactionNotFoundException_whenReferenceIdNotFound() {
		UUID refId = UUID.randomUUID();
		when(transactionRepository.findByReferenceId(refId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> transactionService.getTransactionByReferenceId(refId))
				.isInstanceOf(TransactionNotFoundException.class).hasMessageContaining(refId.toString());
	}

	@Test
	@DisplayName("Should return paginated user transactions")
	void shouldReturnUserTransactions_paginated() {
		Pageable pageable = PageRequest.of(0, 10);
		Transaction tx = Transaction.builder().referenceId(UUID.randomUUID()).sender(sender).receiver(receiver)
				.senderUpiId("alice@payflow").receiverUpiId("bob@payflow").amount(new BigDecimal("100.00"))
				.status(TransactionStatus.COMPLETED).type(TransactionType.TRANSFER).build();
		Page<Transaction> page = new PageImpl<>(List.of(tx), pageable, 1);

		when(transactionRepository.findBySenderUpiIdOrReceiverUpiId("alice@payflow", "alice@payflow", pageable))
				.thenReturn(page);

		Page<Transaction> result = transactionService.getUserTransactions("alice@payflow", pageable);

		assertThat(result).isNotNull();
		assertThat(result.getContent()).hasSize(1);
	}
}
