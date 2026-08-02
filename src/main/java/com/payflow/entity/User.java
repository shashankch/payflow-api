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

@Entity
@Table(name = "users")
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

	public User() {
	}

	public User(Long userId, String name, String upiId, BigDecimal balance, String phoneNumber, Long version,
			Instant createdAt, Instant updatedAt) {
		this.userId = userId;
		this.name = name;
		this.upiId = upiId;
		this.balance = balance;
		this.phoneNumber = phoneNumber;
		this.version = version;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
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

	public BigDecimal getBalance() {
		return balance;
	}

	public void setBalance(BigDecimal balance) {
		this.balance = balance;
	}

	public String getPhoneNumber() {
		return phoneNumber;
	}

	public void setPhoneNumber(String phoneNumber) {
		this.phoneNumber = phoneNumber;
	}

	public Long getVersion() {
		return version;
	}

	public void setVersion(Long version) {
		this.version = version;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

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

	public static UserBuilder builder() {
		return new UserBuilder();
	}

	public static class UserBuilder {
		private Long userId;
		private String name;
		private String upiId;
		private BigDecimal balance;
		private String phoneNumber;
		private Long version;
		private Instant createdAt;
		private Instant updatedAt;

		public UserBuilder userId(Long userId) {
			this.userId = userId;
			return this;
		}

		public UserBuilder name(String name) {
			this.name = name;
			return this;
		}

		public UserBuilder upiId(String upiId) {
			this.upiId = upiId;
			return this;
		}

		public UserBuilder balance(BigDecimal balance) {
			this.balance = balance;
			return this;
		}

		public UserBuilder phoneNumber(String phoneNumber) {
			this.phoneNumber = phoneNumber;
			return this;
		}

		public UserBuilder version(Long version) {
			this.version = version;
			return this;
		}

		public UserBuilder createdAt(Instant createdAt) {
			this.createdAt = createdAt;
			return this;
		}

		public UserBuilder updatedAt(Instant updatedAt) {
			this.updatedAt = updatedAt;
			return this;
		}

		public User build() {
			return new User(userId, name, upiId, balance, phoneNumber, version, createdAt, updatedAt);
		}
	}
}
