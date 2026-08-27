package com.payflow.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.payflow.config.MetricsConfig;
import com.payflow.dto.request.TransferMoneyRequest;
import com.payflow.entity.BalanceLedgerEntry;
import com.payflow.entity.LedgerEntryType;
import com.payflow.entity.Transaction;
import com.payflow.entity.TransactionStatus;
import com.payflow.entity.TransactionType;
import com.payflow.entity.User;
import com.payflow.event.TransferCompletedEvent;
import com.payflow.exception.ForbiddenOperationException;
import com.payflow.exception.SelfTransferException;
import com.payflow.exception.TransactionNotFoundException;
import com.payflow.exception.UserNotFoundException;
import com.payflow.repository.BalanceLedgerRepository;
import com.payflow.repository.TransactionRepository;
import com.payflow.repository.UserRepository;
import com.payflow.security.SecurityUtils;

import io.micrometer.observation.annotation.Observed;

@Service
public class TransactionService {

	private static final Logger LOG = LoggerFactory.getLogger(TransactionService.class);

	private final TransactionRepository transactionRepository;
	private final UserRepository userRepository;
	private final BalanceLedgerRepository balanceLedgerRepository;
	private final ApplicationEventPublisher eventPublisher;
	private final MetricsConfig metricsConfig;

	public TransactionService(TransactionRepository transactionRepository, UserRepository userRepository,
			BalanceLedgerRepository balanceLedgerRepository, ApplicationEventPublisher eventPublisher,
			MetricsConfig metricsConfig) {
		this.transactionRepository = transactionRepository;
		this.userRepository = userRepository;
		this.balanceLedgerRepository = balanceLedgerRepository;
		this.eventPublisher = eventPublisher;
		this.metricsConfig = metricsConfig;
	}

	@Observed(name = "payflow.transfers.send", contextualName = "send-money-transfer")
	@Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class, timeout = 5)
	public Transaction sendMoney(TransferMoneyRequest request) {
		long startTime = System.currentTimeMillis();
		String senderUpi = request.getSenderUpiId();
		String receiverUpi = request.getReceiverUpiId();

		String authenticatedUpi = SecurityUtils.getAuthenticatedUpiId();
		if (authenticatedUpi != null && !authenticatedUpi.equalsIgnoreCase(senderUpi)) {
			metricsConfig.incrementTransferStatus("FORBIDDEN");
			throw new ForbiddenOperationException("Authenticated user '" + authenticatedUpi
					+ "' is not authorized to transfer from '" + senderUpi + "'");
		}

		if (senderUpi.equalsIgnoreCase(receiverUpi)) {
			throw new SelfTransferException(senderUpi);
		}

		LOG.info("Initiating P2P transfer of {} from {} to {}", request.getAmount(), senderUpi, receiverUpi);

		// Deterministic lock acquisition order (alphabetical by UPI ID) to prevent
		// database deadlocks
		boolean senderFirst = String.CASE_INSENSITIVE_ORDER.compare(senderUpi, receiverUpi) < 0;
		String firstUpi = senderFirst ? senderUpi : receiverUpi;
		String secondUpi = senderFirst ? receiverUpi : senderUpi;

		String firstRole = senderFirst ? "Sender" : "Receiver";
		String secondRole = senderFirst ? "Receiver" : "Sender";

		User firstUser = userRepository.findByUpiIdWithLock(firstUpi)
				.orElseThrow(() -> new UserNotFoundException(firstRole + " not found: " + firstUpi));

		User secondUser = userRepository.findByUpiIdWithLock(secondUpi)
				.orElseThrow(() -> new UserNotFoundException(secondRole + " not found: " + secondUpi));

		User sender = senderFirst ? firstUser : secondUser;
		User receiver = senderFirst ? secondUser : firstUser;

		BigDecimal senderBalanceBefore = sender.getBalance();
		BigDecimal receiverBalanceBefore = receiver.getBalance();

		sender.debit(request.getAmount());
		receiver.credit(request.getAmount());

		BigDecimal senderBalanceAfter = sender.getBalance();
		BigDecimal receiverBalanceAfter = receiver.getBalance();

		userRepository.save(sender);
		userRepository.save(receiver);

		Transaction.TransactionBuilder builder = Transaction.builder();
		builder.sender(sender);
		builder.receiver(receiver);
		builder.senderUpiId(request.getSenderUpiId());
		builder.receiverUpiId(request.getReceiverUpiId());
		builder.amount(request.getAmount());
		builder.status(TransactionStatus.COMPLETED);
		builder.type(TransactionType.TRANSFER);
		builder.note(request.getNote());
		Transaction savedTransaction = transactionRepository.save(builder.build());

		// Double-entry bookkeeping balance ledger audit entries
		BalanceLedgerEntry.BalanceLedgerEntryBuilder sBuilder = BalanceLedgerEntry.builder();
		sBuilder.user(sender).transaction(savedTransaction).entryType(LedgerEntryType.DEBIT);
		sBuilder.amount(request.getAmount());
		sBuilder.balanceBefore(senderBalanceBefore).balanceAfter(senderBalanceAfter);
		BalanceLedgerEntry senderLedger = sBuilder.build();

		BalanceLedgerEntry.BalanceLedgerEntryBuilder rBuilder = BalanceLedgerEntry.builder();
		rBuilder.user(receiver).transaction(savedTransaction).entryType(LedgerEntryType.CREDIT);
		rBuilder.amount(request.getAmount());
		rBuilder.balanceBefore(receiverBalanceBefore).balanceAfter(receiverBalanceAfter);
		BalanceLedgerEntry receiverLedger = rBuilder.build();

		balanceLedgerRepository.save(senderLedger);
		balanceLedgerRepository.save(receiverLedger);

		UUID refId = savedTransaction.getReferenceId();
		BigDecimal amount = savedTransaction.getAmount();
		TransactionStatus status = savedTransaction.getStatus();
		TransferCompletedEvent event = new TransferCompletedEvent(refId, senderUpi, receiverUpi, amount, status,
				senderBalanceAfter, receiverBalanceAfter, Instant.now());
		eventPublisher.publishEvent(event);

		metricsConfig.recordTransfer("COMPLETED", savedTransaction.getAmount().doubleValue(),
				System.currentTimeMillis() - startTime);

		LOG.info("Transfer completed: txId={}, amount={}", savedTransaction.getReferenceId(),
				savedTransaction.getAmount());

		return savedTransaction;
	}

	@Transactional(readOnly = true)
	public Transaction getTransactionByReferenceId(UUID referenceId) {
		String msg = "Transaction not found: " + referenceId;
		Transaction transaction = transactionRepository.findByReferenceId(referenceId)
				.orElseThrow(() -> new TransactionNotFoundException(msg));

		String authenticatedUpi = SecurityUtils.getAuthenticatedUpiId();
		if (authenticatedUpi != null && !authenticatedUpi.equalsIgnoreCase(transaction.getSenderUpiId())
				&& !authenticatedUpi.equalsIgnoreCase(transaction.getReceiverUpiId())) {
			throw new ForbiddenOperationException("Authenticated user '" + authenticatedUpi
					+ "' is not authorized to view transaction: " + referenceId);
		}

		return transaction;
	}

	@Transactional(readOnly = true)
	public Page<Transaction> getUserTransactions(String upiId, Pageable pageable) {
		String authenticatedUpi = SecurityUtils.getAuthenticatedUpiId();
		if (authenticatedUpi != null && !authenticatedUpi.equalsIgnoreCase(upiId)) {
			throw new ForbiddenOperationException("Authenticated user '" + authenticatedUpi
					+ "' is not authorized to view transactions for: " + upiId);
		}

		return transactionRepository.findBySenderUpiIdOrReceiverUpiId(upiId, upiId, pageable);
	}
}
