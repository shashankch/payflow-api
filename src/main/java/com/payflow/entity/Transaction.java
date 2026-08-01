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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
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

	@PrePersist
	public void ensureReferenceId() {
		if (this.referenceId == null) {
			this.referenceId = UUID.randomUUID();
		}
	}
}
