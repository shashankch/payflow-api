package com.payflow.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.payflow.entity.Transaction;
import com.payflow.repository.TransactionRepository;

@Service
public class TransactionService {

	// Spring creates the TransactionRepository bean and injects it during
	// application startup.
	@Autowired
	private TransactionRepository transactionRepository;

	public Transaction sendMoney(Transaction transaction) {
		return transactionRepository.save(transaction);
	}
}
