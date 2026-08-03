package com.payflow.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class GlobalExceptionHandlerTest {

	private GlobalExceptionHandler handler;

	@BeforeEach
	void setUp() {
		handler = new GlobalExceptionHandler();
	}

	@Test
	void testHandleUserNotFound() {
		UserNotFoundException ex = new UserNotFoundException(1L);
		ProblemDetail problem = handler.handleUserNotFound(ex);

		assertEquals(HttpStatus.NOT_FOUND.value(), problem.getStatus());
		assertEquals("User Not Found", problem.getTitle());
		assertEquals("User not found with ID: 1", problem.getDetail());
	}

	@Test
	void testHandleTransactionNotFound() {
		TransactionNotFoundException ex = new TransactionNotFoundException(100L);
		ProblemDetail problem = handler.handleTransactionNotFound(ex);

		assertEquals(HttpStatus.NOT_FOUND.value(), problem.getStatus());
		assertEquals("Transaction Not Found", problem.getTitle());
		assertEquals("Transaction not found with ID: 100", problem.getDetail());
	}

	@Test
	void testHandleDuplicateUpiId() {
		DuplicateUpiIdException ex = new DuplicateUpiIdException("test@upi");
		ProblemDetail problem = handler.handleDuplicateUpiId(ex);

		assertEquals(HttpStatus.CONFLICT.value(), problem.getStatus());
		assertEquals("Duplicate UPI ID", problem.getTitle());
		assertEquals("User already exists with UPI ID: test@upi", problem.getDetail());
	}

	@Test
	void testHandleInsufficientBalance() {
		InsufficientBalanceException ex = new InsufficientBalanceException("Insufficient funds");
		ProblemDetail problem = handler.handleInsufficientBalance(ex);

		assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.value(), problem.getStatus());
		assertEquals("Insufficient Balance", problem.getTitle());
		assertEquals("Insufficient funds", problem.getDetail());
	}

	@Test
	void testHandleSelfTransfer() {
		SelfTransferException ex = new SelfTransferException("test@upi");
		ProblemDetail problem = handler.handleSelfTransfer(ex);

		assertEquals(HttpStatus.BAD_REQUEST.value(), problem.getStatus());
		assertEquals("Self Transfer Prohibited", problem.getTitle());
		assertEquals("Self-transfer is not permitted for UPI ID: test@upi", problem.getDetail());
	}

	@Test
	void testHandleUncaughtException() {
		Exception ex = new RuntimeException("Unexpected error");
		ProblemDetail problem = handler.handleUncaughtException(ex);

		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), problem.getStatus());
		assertEquals("Internal Server Error", problem.getTitle());
		assertEquals("An unexpected internal error occurred", problem.getDetail());
		assertNotNull(problem.getProperties().get("timestamp"));
	}
}
