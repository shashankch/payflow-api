package com.payflow.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.payflow.dto.request.TransferMoneyRequest;
import com.payflow.entity.BalanceLedgerEntry;
import com.payflow.entity.LedgerEntryType;
import com.payflow.entity.Transaction;
import com.payflow.entity.TransactionStatus;
import com.payflow.entity.TransactionType;
import com.payflow.entity.User;
import com.payflow.exception.SelfTransferException;
import com.payflow.exception.TransactionNotFoundException;
import com.payflow.exception.UserNotFoundException;
import com.payflow.repository.BalanceLedgerRepository;
import com.payflow.repository.TransactionRepository;
import com.payflow.repository.UserRepository;

@Service
public class TransactionService {

	private final TransactionRepository transactionRepository;
	private final UserRepository userRepository;
	private final BalanceLedgerRepository balanceLedgerRepository;

	public TransactionService(TransactionRepository transactionRepository, UserRepository userRepository,
			BalanceLedgerRepository balanceLedgerRepository) {
		this.transactionRepository = transactionRepository;
		this.userRepository = userRepository;
		this.balanceLedgerRepository = balanceLedgerRepository;
	}

	@Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class, timeout = 5)
	public Transaction sendMoney(TransferMoneyRequest request) {
		String senderUpi = request.getSenderUpiId();
		String receiverUpi = request.getReceiverUpiId();

		if (senderUpi.equalsIgnoreCase(receiverUpi)) {
			throw new SelfTransferException(senderUpi);
		}

		// Deterministic lock acquisition order (alphabetical by UPI ID) to prevent
		// database deadlocks
		boolean senderFirst = senderUpi.compareToIgnoreCase(receiverUpi) < 0;
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

		return savedTransaction;
	}

	@Transactional(readOnly = true)
	public Transaction getTransactionByReferenceId(UUID referenceId) {
		String msg = "Transaction not found: " + referenceId;
		return transactionRepository.findByReferenceId(referenceId)
				.orElseThrow(() -> new TransactionNotFoundException(msg));
	}

	@Transactional(readOnly = true)
	public Page<Transaction> getUserTransactions(String upiId, Pageable pageable) {
		return transactionRepository.findBySenderUpiIdOrReceiverUpiId(upiId, upiId, pageable);
	}
}
