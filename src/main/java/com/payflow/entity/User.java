package com.payflow.entity;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long userId;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false, unique = true, length = 30)
	private String upiId;

	@Column(precision = 19, scale = 4, nullable = false)
	private BigDecimal balance;

	@Column(nullable = false, length = 15)
	private String phoneNumber;

	@Version
	private Long version;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private Instant updatedAt;

	public void debit(BigDecimal amount) {
		if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Debit amount must be positive");
		}
		if (this.balance == null || this.balance.compareTo(amount) < 0) {
			throw new IllegalStateException("Insufficient balance for UPI ID: " + this.upiId);
		}
		this.balance = this.balance.subtract(amount);
	}

	public void credit(BigDecimal amount) {
		if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Credit amount must be positive");
		}
		if (this.balance == null) {
			this.balance = BigDecimal.ZERO;
		}
		this.balance = this.balance.add(amount);
	}
}
