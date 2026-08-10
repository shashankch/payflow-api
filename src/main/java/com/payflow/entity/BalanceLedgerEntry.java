package com.payflow.entity;

import java.math.BigDecimal;
import java.time.Instant;

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
import jakarta.persistence.Table;

@Entity
@Table(name = "balance_ledger")
public class BalanceLedgerEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long ledgerId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "transaction_id", nullable = false)
	private Transaction transaction;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private LedgerEntryType entryType;

	@Column(precision = 19, scale = 4, nullable = false)
	private BigDecimal amount;

	@Column(precision = 19, scale = 4, nullable = false)
	private BigDecimal balanceBefore;

	@Column(precision = 19, scale = 4, nullable = false)
	private BigDecimal balanceAfter;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	public BalanceLedgerEntry() {
	}

	public BalanceLedgerEntry(Long ledgerId, User user, Transaction transaction, LedgerEntryType entryType,
			BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter, Instant createdAt) {
		this.ledgerId = ledgerId;
		this.user = user;
		this.transaction = transaction;
		this.entryType = entryType;
		this.amount = amount;
		this.balanceBefore = balanceBefore;
		this.balanceAfter = balanceAfter;
		this.createdAt = createdAt;
	}

	public Long getLedgerId() {
		return ledgerId;
	}

	public void setLedgerId(Long ledgerId) {
		this.ledgerId = ledgerId;
	}

	public User getUser() {
		return user;
	}

	public void setUser(User user) {
		this.user = user;
	}

	public Transaction getTransaction() {
		return transaction;
	}

	public void setTransaction(Transaction transaction) {
		this.transaction = transaction;
	}

	public LedgerEntryType getEntryType() {
		return entryType;
	}

	public void setEntryType(LedgerEntryType entryType) {
		this.entryType = entryType;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public BigDecimal getBalanceBefore() {
		return balanceBefore;
	}

	public void setBalanceBefore(BigDecimal balanceBefore) {
		this.balanceBefore = balanceBefore;
	}

	public BigDecimal getBalanceAfter() {
		return balanceAfter;
	}

	public void setBalanceAfter(BigDecimal balanceAfter) {
		this.balanceAfter = balanceAfter;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public static BalanceLedgerEntryBuilder builder() {
		return new BalanceLedgerEntryBuilder();
	}

	public static class BalanceLedgerEntryBuilder {
		private Long ledgerId;
		private User user;
		private Transaction transaction;
		private LedgerEntryType entryType;
		private BigDecimal amount;
		private BigDecimal balanceBefore;
		private BigDecimal balanceAfter;
		private Instant createdAt;

		BalanceLedgerEntryBuilder() {
		}

		public BalanceLedgerEntryBuilder ledgerId(Long ledgerId) {
			this.ledgerId = ledgerId;
			return this;
		}

		public BalanceLedgerEntryBuilder user(User user) {
			this.user = user;
			return this;
		}

		public BalanceLedgerEntryBuilder transaction(Transaction transaction) {
			this.transaction = transaction;
			return this;
		}

		public BalanceLedgerEntryBuilder entryType(LedgerEntryType entryType) {
			this.entryType = entryType;
			return this;
		}

		public BalanceLedgerEntryBuilder amount(BigDecimal amount) {
			this.amount = amount;
			return this;
		}

		public BalanceLedgerEntryBuilder balanceBefore(BigDecimal balanceBefore) {
			this.balanceBefore = balanceBefore;
			return this;
		}

		public BalanceLedgerEntryBuilder balanceAfter(BigDecimal balanceAfter) {
			this.balanceAfter = balanceAfter;
			return this;
		}

		public BalanceLedgerEntryBuilder createdAt(Instant createdAt) {
			this.createdAt = createdAt;
			return this;
		}

		public BalanceLedgerEntry build() {
			BalanceLedgerEntry entry = new BalanceLedgerEntry();
			entry.setLedgerId(ledgerId);
			entry.setUser(user);
			entry.setTransaction(transaction);
			entry.setEntryType(entryType);
			entry.setAmount(amount);
			entry.setBalanceBefore(balanceBefore);
			entry.setBalanceAfter(balanceAfter);
			entry.setCreatedAt(createdAt);
			return entry;
		}
	}
}
