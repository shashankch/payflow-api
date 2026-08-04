package com.payflow.exception;

public class UserNotFoundException extends PayflowException {

	public UserNotFoundException(String message) {
		super(message);
	}

	public UserNotFoundException(Long userId) {
		super("User not found with ID: " + userId);
	}
}
