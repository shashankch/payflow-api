package com.payflow.exception;

public class TransactionNotFoundException extends PayflowException {

	public TransactionNotFoundException(String message) {
		super(message);
	}

	public TransactionNotFoundException(Long transactionId) {
		super("Transaction not found with ID: " + transactionId);
	}
}
