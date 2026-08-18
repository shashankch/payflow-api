package com.payflow.exception;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(UserNotFoundException.class)
	public ProblemDetail handleUserNotFound(UserNotFoundException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
		problem.setType(URI.create("https://api.payflow.com/errors/user-not-found"));
		problem.setTitle("User Not Found");
		enrichProblemDetail(problem);
		return problem;
	}

	@ExceptionHandler(TransactionNotFoundException.class)
	public ProblemDetail handleTransactionNotFound(TransactionNotFoundException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
		problem.setType(URI.create("https://api.payflow.com/errors/transaction-not-found"));
		problem.setTitle("Transaction Not Found");
		enrichProblemDetail(problem);
		return problem;
	}

	@ExceptionHandler(DuplicateUpiIdException.class)
	public ProblemDetail handleDuplicateUpiId(DuplicateUpiIdException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
		problem.setType(URI.create("https://api.payflow.com/errors/duplicate-upi-id"));
		problem.setTitle("Duplicate UPI ID");
		enrichProblemDetail(problem);
		return problem;
	}

	@ExceptionHandler(InsufficientBalanceException.class)
	public ProblemDetail handleInsufficientBalance(InsufficientBalanceException ex) {
		HttpStatus status = HttpStatus.UNPROCESSABLE_ENTITY;
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
		problem.setType(URI.create("https://api.payflow.com/errors/insufficient-balance"));
		problem.setTitle("Insufficient Balance");
		enrichProblemDetail(problem);
		return problem;
	}

	@ExceptionHandler(SelfTransferException.class)
	public ProblemDetail handleSelfTransfer(SelfTransferException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
		problem.setType(URI.create("https://api.payflow.com/errors/self-transfer-prohibited"));
		problem.setTitle("Self Transfer Prohibited");
		enrichProblemDetail(problem);
		return problem;
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail handleValidationException(MethodArgumentNotValidException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
				"Validation failed for request parameters");
		problem.setType(URI.create("https://api.payflow.com/errors/validation-error"));
		problem.setTitle("Validation Failure");

		Map<String, String> fieldErrors = new HashMap<>();
		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			fieldErrors.put(error.getField(), error.getDefaultMessage());
		}
		problem.setProperty("errors", fieldErrors);
		enrichProblemDetail(problem);
		return problem;
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
		LOG.warn("Data integrity violation: {}", ex.getMessage());
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"Database constraint violation occurred");
		problem.setType(URI.create("https://api.payflow.com/errors/data-integrity-violation"));
		problem.setTitle("Data Integrity Violation");
		enrichProblemDetail(problem);
		return problem;
	}

	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUncaughtException(Exception ex) {
		LOG.error("Unhandled server exception: ", ex);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
				"An unexpected internal error occurred");
		problem.setType(URI.create("https://api.payflow.com/errors/internal-server-error"));
		problem.setTitle("Internal Server Error");
		enrichProblemDetail(problem);
		return problem;
	}

	private void enrichProblemDetail(ProblemDetail problem) {
		problem.setProperty("timestamp", Instant.now().toString());
		String requestId = MDC.get("requestId");
		if (requestId != null) {
			problem.setProperty("requestId", requestId);
		}
	}
}
