package com.payflow.exception;

public class DuplicateUpiIdException extends PayflowException {

	public DuplicateUpiIdException(String upiId) {
		super("User already exists with UPI ID: " + upiId);
	}
}
