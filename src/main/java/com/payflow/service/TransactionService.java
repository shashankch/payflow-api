package com.payflow.service;

import org.springframework.stereotype.Service;

import com.payflow.dto.request.TransferMoneyRequest;
import com.payflow.entity.Transaction;
import com.payflow.entity.TransactionStatus;
import com.payflow.entity.TransactionType;
import com.payflow.repository.TransactionRepository;

@Service
public class TransactionService {

	private final TransactionRepository transactionRepository;

	public TransactionService(TransactionRepository transactionRepository) {
		this.transactionRepository = transactionRepository;
	}

	public Transaction sendMoney(TransferMoneyRequest request) {
		Transaction.TransactionBuilder builder = Transaction.builder();
		builder.senderUpiId(request.getSenderUpiId());
		builder.receiverUpiId(request.getReceiverUpiId());
		builder.amount(request.getAmount());
		builder.status(TransactionStatus.COMPLETED);
		builder.type(TransactionType.TRANSFER);
		builder.note(request.getNote());
		Transaction transaction = builder.build();
		return transactionRepository.save(transaction);
	}
}
