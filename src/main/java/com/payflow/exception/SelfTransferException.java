package com.payflow.exception;

public class SelfTransferException extends PayflowException {

	public SelfTransferException(String upiId) {
		super("Self-transfer is not permitted for UPI ID: " + upiId);
	}
}
