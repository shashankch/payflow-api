package com.payflow.entity;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserTest {

	private User user;

	@BeforeEach
	void setUp() {
		user = User.builder().name("John Doe").upiId("john@upi").phoneNumber("9876543210")
				.balance(new BigDecimal("500.00")).build();
	}

	@Test
	@DisplayName("Should debit amount successfully when balance is sufficient")
	void shouldDebitSuccessfully_whenBalanceIsSufficient() {
		user.debit(new BigDecimal("200.00"));
		assertEquals(new BigDecimal("300.00"), user.getBalance());
	}

	@Test
	@DisplayName("Should throw IllegalStateException when debiting more than current balance")
	void shouldThrowException_whenDebitingMoreThanBalance() {
		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> user.debit(new BigDecimal("600.00")));
		assertEquals("Insufficient balance for UPI ID: john@upi", exception.getMessage());
	}

	@Test
	@DisplayName("Should throw IllegalArgumentException when debiting zero or negative amount")
	void shouldThrowException_whenDebitingInvalidAmount() {
		assertThrows(IllegalArgumentException.class, () -> user.debit(BigDecimal.ZERO));
		assertThrows(IllegalArgumentException.class, () -> user.debit(new BigDecimal("-50.00")));
	}

	@Test
	@DisplayName("Should credit amount successfully")
	void shouldCreditSuccessfully() {
		user.credit(new BigDecimal("150.00"));
		assertEquals(new BigDecimal("650.00"), user.getBalance());
	}

	@Test
	@DisplayName("Should throw IllegalArgumentException when crediting zero or negative amount")
	void shouldThrowException_whenCreditingInvalidAmount() {
		assertThrows(IllegalArgumentException.class, () -> user.credit(BigDecimal.ZERO));
		assertThrows(IllegalArgumentException.class, () -> user.credit(new BigDecimal("-10.00")));
	}
}
