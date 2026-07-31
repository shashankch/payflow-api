package com.payflow.service;

import org.springframework.stereotype.Service;

import com.payflow.entity.Transaction;
import com.payflow.repository.TransactionRepository;

@Service
public class TransactionService {

	private final TransactionRepository transactionRepository;

	public TransactionService(TransactionRepository transactionRepository) {
		this.transactionRepository = transactionRepository;
	}

	public Transaction sendMoney(Transaction transaction) {
		return transactionRepository.save(transaction);
	}
}
