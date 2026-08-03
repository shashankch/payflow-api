package com.payflow.exception;

public abstract class PayflowException extends RuntimeException {

	protected PayflowException(String message) {
		super(message);
	}

	protected PayflowException(String message, Throwable cause) {
		super(message, cause);
	}
}
