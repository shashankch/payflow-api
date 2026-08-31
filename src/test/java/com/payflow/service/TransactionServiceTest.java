package com.payflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.payflow.dto.request.TransferMoneyRequest;
import com.payflow.entity.BalanceLedgerEntry;
import com.payflow.entity.LedgerEntryType;
import com.payflow.entity.Transaction;
import com.payflow.entity.TransactionStatus;
import com.payflow.entity.TransactionType;
import com.payflow.entity.User;
import com.payflow.event.TransferCompletedEvent;
import com.payflow.exception.ForbiddenOperationException;
import com.payflow.exception.InsufficientBalanceException;
import com.payflow.exception.SelfTransferException;
import com.payflow.exception.TransactionNotFoundException;
import com.payflow.exception.UserNotFoundException;
import com.payflow.repository.BalanceLedgerRepository;
import com.payflow.repository.TransactionRepository;
import com.payflow.config.MetricsConfig;
import com.payflow.repository.UserRepository;

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

	@Mock
	private MetricsConfig metricsConfig;

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

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
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
		assertThat(sender.getBalance()).isEqualByComparingTo("400.00");
		assertThat(receiver.getBalance()).isEqualByComparingTo("300.00");

		verify(userRepository).save(sender);
		verify(userRepository).save(receiver);
		verify(transactionRepository).save(any(Transaction.class));
		verify(balanceLedgerRepository, times(2)).save(ledgerCaptor.capture());
		verify(eventPublisher).publishEvent(eventCaptor.capture());

		List<BalanceLedgerEntry> ledgers = ledgerCaptor.getAllValues();
		assertThat(ledgers).hasSize(2);
		assertThat(ledgers.get(0).getEntryType()).isEqualTo(LedgerEntryType.DEBIT);
		assertThat(ledgers.get(0).getBalanceBefore()).isEqualByComparingTo("500.00");
		assertThat(ledgers.get(0).getBalanceAfter()).isEqualByComparingTo("400.00");
		assertThat(ledgers.get(1).getEntryType()).isEqualTo(LedgerEntryType.CREDIT);
		assertThat(ledgers.get(1).getBalanceBefore()).isEqualByComparingTo("200.00");
		assertThat(ledgers.get(1).getBalanceAfter()).isEqualByComparingTo("300.00");

		TransferCompletedEvent publishedEvent = eventCaptor.getValue();
		assertThat(publishedEvent).isNotNull();
		assertThat(publishedEvent.senderUpi()).isEqualTo("alice@payflow");
		assertThat(publishedEvent.receiverUpi()).isEqualTo("bob@payflow");
		assertThat(publishedEvent.amount()).isEqualByComparingTo("100.00");
		assertThat(publishedEvent.status()).isEqualTo(TransactionStatus.COMPLETED);
		assertThat(publishedEvent.senderAfter()).isEqualByComparingTo("400.00");
		assertThat(publishedEvent.receiverAfter()).isEqualByComparingTo("300.00");
	}

	@Test
	@DisplayName("Should throw ForbiddenOperationException when authenticated user attempts transfer from different sender UPI")
	void shouldThrowForbidden_whenAuthenticatedUserIsNotSender() {
		SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken("mallory@payflow", null, List.of()));

		TransferMoneyRequest request = TransferMoneyRequest.builder().senderUpiId("alice@payflow")
				.receiverUpiId("bob@payflow").amount(new BigDecimal("100.00")).build();

		assertThatThrownBy(() -> transactionService.sendMoney(request)).isInstanceOf(ForbiddenOperationException.class)
				.hasMessageContaining("not authorized to transfer from");
	}

	@Test
	@DisplayName("Should acquire pessimistic locks in deterministic alphabetical order to avoid deadlocks")
	void shouldAcquireLocksInAlphabeticalOrder() {
		TransferMoneyRequest request = TransferMoneyRequest.builder().senderUpiId("bob@payflow")
				.receiverUpiId("alice@payflow").amount(new BigDecimal("50.00")).build();

		when(userRepository.findByUpiIdWithLock("alice@payflow")).thenReturn(Optional.of(sender));
		when(userRepository.findByUpiIdWithLock("bob@payflow")).thenReturn(Optional.of(receiver));
		when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

		transactionService.sendMoney(request);

		InOrder inOrder = inOrder(userRepository);
		inOrder.verify(userRepository).findByUpiIdWithLock("alice@payflow");
		inOrder.verify(userRepository).findByUpiIdWithLock("bob@payflow");
	}

	@Test
	@DisplayName("Should throw SelfTransferException when sender and receiver UPI IDs are identical")
	void shouldThrowSelfTransferException_whenSenderEqualsReceiver() {
		TransferMoneyRequest request = TransferMoneyRequest.builder().senderUpiId("alice@payflow")
				.receiverUpiId("alice@payflow").amount(new BigDecimal("50.00")).build();

		assertThatThrownBy(() -> transactionService.sendMoney(request)).isInstanceOf(SelfTransferException.class);
	}

	@Test
	@DisplayName("Should throw InsufficientBalanceException when sender does not have enough funds")
	void shouldThrowInsufficientBalanceException_whenSenderBalanceIsLow() {
		TransferMoneyRequest request = TransferMoneyRequest.builder().senderUpiId("alice@payflow")
				.receiverUpiId("bob@payflow").amount(new BigDecimal("9999.00")).build();

		when(userRepository.findByUpiIdWithLock("alice@payflow")).thenReturn(Optional.of(sender));
		when(userRepository.findByUpiIdWithLock("bob@payflow")).thenReturn(Optional.of(receiver));

		assertThatThrownBy(() -> transactionService.sendMoney(request))
				.isInstanceOf(InsufficientBalanceException.class);
	}

	@Test
	@DisplayName("Should throw UserNotFoundException when sender is not found")
	void shouldThrowUserNotFoundException_whenSenderNotFound() {
		TransferMoneyRequest request = TransferMoneyRequest.builder().senderUpiId("alice@payflow")
				.receiverUpiId("bob@payflow").amount(new BigDecimal("50.00")).build();

		when(userRepository.findByUpiIdWithLock("alice@payflow")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> transactionService.sendMoney(request)).isInstanceOf(UserNotFoundException.class);
	}

	@Test
	@DisplayName("Should throw UserNotFoundException when receiver is not found")
	void shouldThrowUserNotFoundException_whenReceiverNotFound() {
		TransferMoneyRequest request = TransferMoneyRequest.builder().senderUpiId("alice@payflow")
				.receiverUpiId("bob@payflow").amount(new BigDecimal("50.00")).build();

		when(userRepository.findByUpiIdWithLock("alice@payflow")).thenReturn(Optional.of(sender));
		when(userRepository.findByUpiIdWithLock("bob@payflow")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> transactionService.sendMoney(request)).isInstanceOf(UserNotFoundException.class);
	}

	@Test
	@DisplayName("Should return transaction when found by reference ID")
	void shouldReturnTransaction_whenFoundByReferenceId() {
		UUID refId = UUID.randomUUID();
		Transaction tx = Transaction.builder().transactionId(10L).referenceId(refId).senderUpiId("alice@payflow")
				.receiverUpiId("bob@payflow").amount(new BigDecimal("75.00")).status(TransactionStatus.COMPLETED)
				.build();

		when(transactionRepository.findByReferenceId(refId)).thenReturn(Optional.of(tx));

		Transaction found = transactionService.getTransactionByReferenceId(refId);
		assertThat(found).isNotNull();
		assertThat(found.getReferenceId()).isEqualTo(refId);
		assertThat(found.getAmount()).isEqualByComparingTo("75.00");
	}

	@Test
	@DisplayName("Should throw ForbiddenOperationException when user is not participant in transaction")
	void shouldThrowForbidden_whenAuthenticatedUserIsNotTransactionParticipant() {
		SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken("mallory@payflow", null, List.of()));

		UUID refId = UUID.randomUUID();
		Transaction tx = Transaction.builder().transactionId(10L).referenceId(refId).senderUpiId("alice@payflow")
				.receiverUpiId("bob@payflow").amount(new BigDecimal("75.00")).status(TransactionStatus.COMPLETED)
				.build();

		when(transactionRepository.findByReferenceId(refId)).thenReturn(Optional.of(tx));

		assertThatThrownBy(() -> transactionService.getTransactionByReferenceId(refId))
				.isInstanceOf(ForbiddenOperationException.class)
				.hasMessageContaining("not authorized to view transaction");
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

	@Test
	@DisplayName("Should throw ForbiddenOperationException when user attempts to view another user's transactions")
	void shouldThrowForbidden_whenAuthenticatedUserViewsAnotherUserTransactions() {
		SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken("mallory@payflow", null, List.of()));

		Pageable pageable = PageRequest.of(0, 10);

		assertThatThrownBy(() -> transactionService.getUserTransactions("alice@payflow", pageable))
				.isInstanceOf(ForbiddenOperationException.class)
				.hasMessageContaining("not authorized to view transactions");
	}
}
