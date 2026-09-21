package com.payflow.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;

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
	void testHandleForbiddenOperation() {
		ForbiddenOperationException ex = new ForbiddenOperationException("Unauthorized access");
		ProblemDetail problem = handler.handleForbiddenOperation(ex);

		assertEquals(HttpStatus.FORBIDDEN.value(), problem.getStatus());
		assertEquals("Forbidden Operation", problem.getTitle());
		assertEquals("Unauthorized access", problem.getDetail());
	}

	@Test
	void testHandleAccessDenied() {
		AccessDeniedException ex = new AccessDeniedException("Access is denied");
		ProblemDetail problem = handler.handleAccessDenied(ex);

		assertEquals(HttpStatus.FORBIDDEN.value(), problem.getStatus());
		assertEquals("Access Denied", problem.getTitle());
		assertEquals("Access is denied", problem.getDetail());
	}

	@Test
	void testHandleInvalidUpi() {
		InvalidUpiException ex = new InvalidUpiException("UPI ID is invalid: bad@upi");
		ProblemDetail problem = handler.handleInvalidUpi(ex);

		assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.value(), problem.getStatus());
		assertEquals("Invalid UPI ID", problem.getTitle());
		assertEquals("UPI ID is invalid: bad@upi", problem.getDetail());
	}

	@Test
	void testHandleRequestNotPermitted() {
		RateLimiter rateLimiter = RateLimiter.ofDefaults("testLimiter");
		RequestNotPermitted ex = RequestNotPermitted.createRequestNotPermitted(rateLimiter);
		ResponseEntity<ProblemDetail> response = handler.handleRequestNotPermitted(ex);

		assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
		assertEquals("1", response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
		ProblemDetail problem = response.getBody();
		assertNotNull(problem);
		assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), problem.getStatus());
		assertEquals("Rate Limit Exceeded", problem.getTitle());
	}

	@Test
	void testHandleCallNotPermitted() {
		CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("testBreaker");
		CallNotPermittedException ex = CallNotPermittedException.createCallNotPermittedException(circuitBreaker);
		ResponseEntity<ProblemDetail> response = handler.handleCallNotPermitted(ex);

		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
		ProblemDetail problem = response.getBody();
		assertNotNull(problem);
		assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), problem.getStatus());
		assertEquals("Service Unavailable", problem.getTitle());
	}

	@Test
	void testHandleDataIntegrityViolation() {
		DataIntegrityViolationException ex = new DataIntegrityViolationException("foreign key constraint");
		ProblemDetail problem = handler.handleDataIntegrityViolation(ex);

		assertEquals(HttpStatus.CONFLICT.value(), problem.getStatus());
		assertEquals("Data Integrity Violation", problem.getTitle());
	}

	@Test
	void testHandleConstraintViolationException() {
		ConstraintViolation<?> violation = mock(ConstraintViolation.class);
		Path path = mock(Path.class);
		when(path.toString()).thenReturn("amount");
		when(violation.getPropertyPath()).thenReturn(path);
		when(violation.getMessage()).thenReturn("must be greater than 0");

		ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation));
		ProblemDetail problem = handler.handleConstraintViolationException(ex);

		assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.value(), problem.getStatus());
		assertEquals("Validation Failure", problem.getTitle());
		assertNotNull(problem.getProperties().get("errors"));
	}

	@Test
	void testHandleMethodArgumentTypeMismatch() {
		MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException("abc", Integer.class,
				"pageSize", null, null);
		ProblemDetail problem = handler.handleMethodArgumentTypeMismatch(ex);

		assertEquals(HttpStatus.BAD_REQUEST.value(), problem.getStatus());
		assertEquals("Parameter Type Mismatch", problem.getTitle());
		assertEquals("Invalid value 'abc' for parameter 'pageSize'", problem.getDetail());
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
