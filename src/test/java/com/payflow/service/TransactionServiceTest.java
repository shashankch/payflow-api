package com.payflow.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.payflow.dto.request.TransferMoneyRequest;
import com.payflow.entity.BalanceLedgerEntry;
import com.payflow.entity.LedgerEntryType;
import com.payflow.entity.Transaction;
import com.payflow.entity.TransactionStatus;
import com.payflow.entity.TransactionType;
import com.payflow.entity.User;
import com.payflow.event.TransferCompletedEvent;
import com.payflow.exception.InsufficientBalanceException;
import com.payflow.exception.SelfTransferException;
import com.payflow.exception.TransactionNotFoundException;
import com.payflow.exception.UserNotFoundException;
import com.payflow.repository.BalanceLedgerRepository;
import com.payflow.repository.TransactionRepository;
import com.payflow.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

	@Mock
	private TransactionRepository transactionRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private BalanceLedgerRepository balanceLedgerRepository;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	@InjectMocks
	private TransactionService transactionService;

	@Captor
	private ArgumentCaptor<Transaction> transactionCaptor;

	@Captor
	private ArgumentCaptor<BalanceLedgerEntry> ledgerCaptor;

	@Captor
	private ArgumentCaptor<TransferCompletedEvent> eventCaptor;

	private User sender;
	private User receiver;

	@BeforeEach
	void setUp() {
		sender = User.builder().userId(1L).referenceId(UUID.randomUUID()).name("Alice").upiId("alice@payflow")
				.balance(new BigDecimal("500.00")).version(0L).build();

		receiver = User.builder().userId(2L).referenceId(UUID.randomUUID()).name("Bob").upiId("bob@payflow")
				.balance(new BigDecimal("200.00")).version(0L).build();
	}

	@Test
	@DisplayName("Should successfully transfer funds between two accounts, record double-entry ledgers, and publish domain event")
	void shouldTransferFunds_whenRequestIsValid() {
		TransferMoneyRequest request = TransferMoneyRequest.builder().senderUpiId("alice@payflow")
				.receiverUpiId("bob@payflow").amount(new BigDecimal("100.00")).note("Dinner split").build();

		when(userRepository.findByUpiIdWithLock("alice@payflow")).thenReturn(Optional.of(sender));
		when(userRepository.findByUpiIdWithLock("bob@payflow")).thenReturn(Optional.of(receiver));
		when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Transaction result = transactionService.sendMoney(request);

		assertThat(result).isNotNull();
		assertThat(result.getAmount()).isEqualByComparingTo("100.00");
		assertThat(result.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
		assertThat(result.getType()).isEqualTo(TransactionType.TRANSFER);
		assertThat(result.getSenderUpiId()).isEqualTo("alice@payflow");
		assertThat(result.getReceiverUpiId()).isEqualTo("bob@payflow");

		assertThat(sender.getBalance()).isEqualByComparingTo("400.00");
		assertThat(receiver.getBalance()).isEqualByComparingTo("300.00");

		verify(userRepository).save(sender);
		verify(userRepository).save(receiver);
		verify(transactionRepository).save(any(Transaction.class));
		verify(balanceLedgerRepository, times(2)).save(ledgerCaptor.capture());
		verify(eventPublisher).publishEvent(eventCaptor.capture());

		TransferCompletedEvent publishedEvent = eventCaptor.getValue();
		assertThat(publishedEvent).isNotNull();
		assertThat(publishedEvent.senderUpi()).isEqualTo("alice@payflow");
		assertThat(publishedEvent.receiverUpi()).isEqualTo("bob@payflow");
		assertThat(publishedEvent.amount()).isEqualByComparingTo("100.00");

		List<BalanceLedgerEntry> ledgerEntries = ledgerCaptor.getAllValues();
		assertThat(ledgerEntries).hasSize(2);

		BalanceLedgerEntry debitEntry = ledgerEntries.get(0);
		assertThat(debitEntry.getEntryType()).isEqualTo(LedgerEntryType.DEBIT);
		assertThat(debitEntry.getAmount()).isEqualByComparingTo("100.00");
		assertThat(debitEntry.getBalanceBefore()).isEqualByComparingTo("500.00");
		assertThat(debitEntry.getBalanceAfter()).isEqualByComparingTo("400.00");

		BalanceLedgerEntry creditEntry = ledgerEntries.get(1);
		assertThat(creditEntry.getEntryType()).isEqualTo(LedgerEntryType.CREDIT);
		assertThat(creditEntry.getAmount()).isEqualByComparingTo("100.00");
		assertThat(creditEntry.getBalanceBefore()).isEqualByComparingTo("200.00");
		assertThat(creditEntry.getBalanceAfter()).isEqualByComparingTo("300.00");
	}

	@Test
	@DisplayName("Should acquire locks in alphabetical order by UPI ID regardless of who is sender/receiver")
	void shouldAcquireLocksInAlphabeticalOrder_toPreventDeadlocks() {
		TransferMoneyRequest request = TransferMoneyRequest.builder().senderUpiId("bob@payflow")
				.receiverUpiId("alice@payflow").amount(new BigDecimal("50.00")).build();

		when(userRepository.findByUpiIdWithLock("alice@payflow")).thenReturn(Optional.of(receiver));
		when(userRepository.findByUpiIdWithLock("bob@payflow")).thenReturn(Optional.of(sender));
		when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

		transactionService.sendMoney(request);

		InOrder inOrder = inOrder(userRepository);
		inOrder.verify(userRepository).findByUpiIdWithLock("alice@payflow");
		inOrder.verify(userRepository).findByUpiIdWithLock("bob@payflow");
	}

	@Test
	@DisplayName("Should throw InsufficientBalanceException when sender does not have enough balance")
	void shouldThrowInsufficientBalanceException_whenSenderBalanceIsLow() {
		TransferMoneyRequest request = TransferMoneyRequest.builder().senderUpiId("alice@payflow")
				.receiverUpiId("bob@payflow").amount(new BigDecimal("1000.00")).build();

		when(userRepository.findByUpiIdWithLock("alice@payflow")).thenReturn(Optional.of(sender));
		when(userRepository.findByUpiIdWithLock("bob@payflow")).thenReturn(Optional.of(receiver));

		assertThatThrownBy(() -> transactionService.sendMoney(request)).isInstanceOf(InsufficientBalanceException.class)
				.hasMessageContaining("Insufficient balance");
	}

	@Test
	@DisplayName("Should throw SelfTransferException when sender and receiver UPI IDs are identical")
	void shouldThrowSelfTransferException_whenSenderEqualsReceiver() {
		TransferMoneyRequest request = TransferMoneyRequest.builder().senderUpiId("alice@payflow")
				.receiverUpiId("alice@payflow").amount(new BigDecimal("50.00")).build();

		assertThatThrownBy(() -> transactionService.sendMoney(request)).isInstanceOf(SelfTransferException.class);
	}

	@Test
	@DisplayName("Should throw UserNotFoundException when sender does not exist")
	void shouldThrowUserNotFoundException_whenSenderNotFound() {
		TransferMoneyRequest request = TransferMoneyRequest.builder().senderUpiId("unknown@payflow")
				.receiverUpiId("bob@payflow").amount(new BigDecimal("50.00")).build();

		when(userRepository.findByUpiIdWithLock(any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> transactionService.sendMoney(request)).isInstanceOf(UserNotFoundException.class);
	}

	@Test
	@DisplayName("Should return transaction by reference ID when found")
	void shouldReturnTransaction_whenFoundByReferenceId() {
		UUID refId = UUID.randomUUID();
		Transaction tx = Transaction.builder().transactionId(10L).referenceId(refId).amount(new BigDecimal("75.00"))
				.status(TransactionStatus.COMPLETED).build();

		when(transactionRepository.findByReferenceId(refId)).thenReturn(Optional.of(tx));

		Transaction found = transactionService.getTransactionByReferenceId(refId);
		assertThat(found).isNotNull();
		assertThat(found.getReferenceId()).isEqualTo(refId);
		assertThat(found.getAmount()).isEqualByComparingTo("75.00");
	}

	@Test
	@DisplayName("Should throw TransactionNotFoundException when transaction does not exist by reference ID")
	void shouldThrowTransactionNotFoundException_whenReferenceIdNotFound() {
		UUID missingRefId = UUID.randomUUID();
		when(transactionRepository.findByReferenceId(missingRefId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> transactionService.getTransactionByReferenceId(missingRefId))
				.isInstanceOf(TransactionNotFoundException.class);
	}

	@Test
	@DisplayName("Should return paginated transaction history for a given user UPI ID")
	void shouldReturnPaginatedUserTransactions() {
		Transaction tx1 = Transaction.builder().transactionId(1L).amount(new BigDecimal("50.00")).build();
		Transaction tx2 = Transaction.builder().transactionId(2L).amount(new BigDecimal("75.00")).build();
		Page<Transaction> page = new PageImpl<>(List.of(tx1, tx2));
		Pageable pageable = PageRequest.of(0, 10);

		when(transactionRepository.findBySenderUpiIdOrReceiverUpiId("alice@payflow", "alice@payflow", pageable))
				.thenReturn(page);

		Page<Transaction> result = transactionService.getUserTransactions("alice@payflow", pageable);
		assertThat(result).isNotNull();
		assertThat(result.getTotalElements()).isEqualTo(2);
	}
}
