package com.payflow.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class TransferMoneyRequest {

	@NotBlank(message = "Sender UPI ID cannot be blank")
	@Size(max = 100, message = "Sender UPI ID must not exceed 100 characters")
	@Pattern(regexp = "^[a-zA-Z0-9.\\-_]{2,64}@[a-zA-Z]{2,32}$", message = "Invalid sender UPI ID format")
	private String senderUpiId;

	@NotBlank(message = "Receiver UPI ID cannot be blank")
	@Size(max = 100, message = "Receiver UPI ID must not exceed 100 characters")
	@Pattern(regexp = "^[a-zA-Z0-9.\\-_]{2,64}@[a-zA-Z]{2,32}$", message = "Invalid receiver UPI ID format")
	private String receiverUpiId;

	@NotNull(message = "Amount cannot be null")
	@DecimalMin(value = "0.01", message = "Transfer amount must be at least 0.01")
	@DecimalMax(value = "1000000.00", message = "Transfer amount must not exceed 1,000,000")
	private BigDecimal amount;

	@Size(max = 255, message = "Note must not exceed 255 characters")
	private String note;

	public TransferMoneyRequest() {
	}

	public TransferMoneyRequest(String senderUpiId, String receiverUpiId, BigDecimal amount, String note) {
		this.senderUpiId = senderUpiId;
		this.receiverUpiId = receiverUpiId;
		this.amount = amount;
		this.note = note;
	}

	public String getSenderUpiId() {
		return senderUpiId;
	}

	public void setSenderUpiId(String senderUpiId) {
		this.senderUpiId = senderUpiId;
	}

	public String getReceiverUpiId() {
		return receiverUpiId;
	}

	public void setReceiverUpiId(String receiverUpiId) {
		this.receiverUpiId = receiverUpiId;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}

	public static TransferMoneyRequestBuilder builder() {
		return new TransferMoneyRequestBuilder();
	}

	public static class TransferMoneyRequestBuilder {
		private String senderUpiId;
		private String receiverUpiId;
		private BigDecimal amount;
		private String note;

		public TransferMoneyRequestBuilder senderUpiId(String senderUpiId) {
			this.senderUpiId = senderUpiId;
			return this;
		}

		public TransferMoneyRequestBuilder receiverUpiId(String receiverUpiId) {
			this.receiverUpiId = receiverUpiId;
			return this;
		}

		public TransferMoneyRequestBuilder amount(BigDecimal amount) {
			this.amount = amount;
			return this;
		}

		public TransferMoneyRequestBuilder note(String note) {
			this.note = note;
			return this;
		}

		public TransferMoneyRequest build() {
			return new TransferMoneyRequest(senderUpiId, receiverUpiId, amount, note);
		}
	}
}
