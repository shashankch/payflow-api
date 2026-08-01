package com.payflow.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "transactions")
public class Transaction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long transactionId;

	@Column(nullable = false, unique = true, updatable = false)
	private UUID referenceId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "sender_id")
	private User sender;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "receiver_id")
	private User receiver;

	@Column(nullable = false, length = 30)
	private String senderUpiId;

	@Column(nullable = false, length = 30)
	private String receiverUpiId;

	@Column(precision = 19, scale = 4, nullable = false)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TransactionStatus status;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TransactionType type;

	@Column(length = 255)
	private String note;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	public Transaction() {
	}

	public Transaction(TransactionBuilder builder) {
		this.transactionId = builder.transactionId;
		this.referenceId = builder.referenceId;
		this.sender = builder.sender;
		this.receiver = builder.receiver;
		this.senderUpiId = builder.senderUpiId;
		this.receiverUpiId = builder.receiverUpiId;
		this.amount = builder.amount;
		this.status = builder.status;
		this.type = builder.type;
		this.note = builder.note;
		this.createdAt = builder.createdAt;
	}

	public Long getTransactionId() {
		return transactionId;
	}

	public void setTransactionId(Long transactionId) {
		this.transactionId = transactionId;
	}

	public UUID getReferenceId() {
		return referenceId;
	}

	public void setReferenceId(UUID referenceId) {
		this.referenceId = referenceId;
	}

	public User getSender() {
		return sender;
	}

	public void setSender(User sender) {
		this.sender = sender;
	}

	public User getReceiver() {
		return receiver;
	}

	public void setReceiver(User receiver) {
		this.receiver = receiver;
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

	public TransactionStatus getStatus() {
		return status;
	}

	public void setStatus(TransactionStatus status) {
		this.status = status;
	}

	public TransactionType getType() {
		return type;
	}

	public void setType(TransactionType type) {
		this.type = type;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	@PrePersist
	public void ensureReferenceId() {
		if (this.referenceId == null) {
			this.referenceId = UUID.randomUUID();
		}
	}

	public static TransactionBuilder builder() {
		return new TransactionBuilder();
	}

	public static class TransactionBuilder {
		private Long transactionId;
		private UUID referenceId;
		private User sender;
		private User receiver;
		private String senderUpiId;
		private String receiverUpiId;
		private BigDecimal amount;
		private TransactionStatus status;
		private TransactionType type;
		private String note;
		private Instant createdAt;

		public TransactionBuilder transactionId(Long transactionId) {
			this.transactionId = transactionId;
			return this;
		}

		public TransactionBuilder referenceId(UUID referenceId) {
			this.referenceId = referenceId;
			return this;
		}

		public TransactionBuilder sender(User sender) {
			this.sender = sender;
			return this;
		}

		public TransactionBuilder receiver(User receiver) {
			this.receiver = receiver;
			return this;
		}

		public TransactionBuilder senderUpiId(String senderUpiId) {
			this.senderUpiId = senderUpiId;
			return this;
		}

		public TransactionBuilder receiverUpiId(String receiverUpiId) {
			this.receiverUpiId = receiverUpiId;
			return this;
		}

		public TransactionBuilder amount(BigDecimal amount) {
			this.amount = amount;
			return this;
		}

		public TransactionBuilder status(TransactionStatus status) {
			this.status = status;
			return this;
		}

		public TransactionBuilder type(TransactionType type) {
			this.type = type;
			return this;
		}

		public TransactionBuilder note(String note) {
			this.note = note;
			return this;
		}

		public TransactionBuilder createdAt(Instant createdAt) {
			this.createdAt = createdAt;
			return this;
		}

		public Transaction build() {
			return new Transaction(this);
		}
	}
}
