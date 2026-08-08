package com.payflow.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.payflow.dto.request.TransferMoneyRequest;
import com.payflow.entity.Transaction;
import com.payflow.entity.TransactionStatus;
import com.payflow.entity.TransactionType;
import com.payflow.entity.User;
import com.payflow.exception.SelfTransferException;
import com.payflow.exception.UserNotFoundException;
import com.payflow.repository.TransactionRepository;
import com.payflow.repository.UserRepository;

@Service
public class TransactionService {

	private final TransactionRepository transactionRepository;
	private final UserRepository userRepository;

	public TransactionService(TransactionRepository transactionRepository, UserRepository userRepository) {
		this.transactionRepository = transactionRepository;
		this.userRepository = userRepository;
	}

	@Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class, timeout = 5)
	public Transaction sendMoney(TransferMoneyRequest request) {
		String senderUpi = request.getSenderUpiId();
		String receiverUpi = request.getReceiverUpiId();

		if (senderUpi.equalsIgnoreCase(receiverUpi)) {
			throw new SelfTransferException(senderUpi);
		}

		User sender = userRepository.findByUpiId(senderUpi)
				.orElseThrow(() -> new UserNotFoundException("Sender not found: " + senderUpi));
		User receiver = userRepository.findByUpiId(receiverUpi)
				.orElseThrow(() -> new UserNotFoundException("Receiver not found: " + receiverUpi));

		sender.debit(request.getAmount());
		receiver.credit(request.getAmount());
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
		return transactionRepository.save(builder.build());
	}

	@Transactional(readOnly = true)
	public Optional<Transaction> getTransactionByReferenceId(UUID referenceId) {
		return transactionRepository.findByReferenceId(referenceId);
	}

	@Transactional(readOnly = true)
	public Page<Transaction> getUserTransactions(String upiId, Pageable pageable) {
		return transactionRepository.findBySenderUpiIdOrReceiverUpiId(upiId, upiId, pageable);
	}
}
