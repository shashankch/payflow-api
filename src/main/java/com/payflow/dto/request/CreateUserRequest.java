package com.payflow.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CreateUserRequest {

	@NotBlank(message = "Name cannot be blank")
	@Size(max = 100, message = "Name must not exceed 100 characters")
	private String name;

	@NotBlank(message = "UPI ID cannot be blank")
	@Pattern(regexp = "^[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z]{2,64}$", message = "Invalid UPI ID format")
	private String upiId;

	@NotBlank(message = "Phone number cannot be blank")
	@Pattern(regexp = "^\\d{10}$", message = "Phone number must be exactly 10 digits")
	private String phoneNumber;

	@NotNull(message = "Balance cannot be null")
	@DecimalMin(value = "0.0", message = "Balance must be non-negative")
	private BigDecimal balance;

	public CreateUserRequest() {
	}

	public CreateUserRequest(String name, String upiId, String phoneNumber, BigDecimal balance) {
		this.name = name;
		this.upiId = upiId;
		this.phoneNumber = phoneNumber;
		this.balance = balance;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getUpiId() {
		return upiId;
	}

	public void setUpiId(String upiId) {
		this.upiId = upiId;
	}

	public String getPhoneNumber() {
		return phoneNumber;
	}

	public void setPhoneNumber(String phoneNumber) {
		this.phoneNumber = phoneNumber;
	}

	public BigDecimal getBalance() {
		return balance;
	}

	public void setBalance(BigDecimal balance) {
		this.balance = balance;
	}

	public static CreateUserRequestBuilder builder() {
		return new CreateUserRequestBuilder();
	}

	public static class CreateUserRequestBuilder {
		private String name;
		private String upiId;
		private String phoneNumber;
		private BigDecimal balance;

		public CreateUserRequestBuilder name(String name) {
			this.name = name;
			return this;
		}

		public CreateUserRequestBuilder upiId(String upiId) {
			this.upiId = upiId;
			return this;
		}

		public CreateUserRequestBuilder phoneNumber(String phoneNumber) {
			this.phoneNumber = phoneNumber;
			return this;
		}

		public CreateUserRequestBuilder balance(BigDecimal balance) {
			this.balance = balance;
			return this;
		}

		public CreateUserRequest build() {
			return new CreateUserRequest(name, upiId, phoneNumber, balance);
		}
	}
}
