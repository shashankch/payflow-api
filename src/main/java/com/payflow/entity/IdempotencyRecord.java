package com.payflow.entity;

import java.time.Instant;
import java.util.Objects;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "idempotency_registry")
public class IdempotencyRecord {

	@Id
	@Column(name = "idempotency_key", length = 255, nullable = false)
	private String idempotencyKey;

	@Column(name = "request_hash", length = 64, nullable = false)
	private String requestHash;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", length = 50, nullable = false)
	private IdempotencyStatus status;

	@Column(name = "response_body", columnDefinition = "TEXT")
	private String responseBody;

	@Column(name = "response_code")
	private Integer responseCode;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public IdempotencyRecord() {
	}

	public IdempotencyRecord(String key, String hash, IdempotencyStatus status, String body, Integer code) {
		this.idempotencyKey = key;
		this.requestHash = hash;
		this.status = status;
		this.responseBody = body;
		this.responseCode = code;
	}

	public static IdempotencyRecordBuilder builder() {
		return new IdempotencyRecordBuilder();
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public void setIdempotencyKey(String idempotencyKey) {
		this.idempotencyKey = idempotencyKey;
	}

	public String getRequestHash() {
		return requestHash;
	}

	public void setRequestHash(String requestHash) {
		this.requestHash = requestHash;
	}

	public IdempotencyStatus getStatus() {
		return status;
	}

	public void setStatus(IdempotencyStatus status) {
		this.status = status;
	}

	public String getResponseBody() {
		return responseBody;
	}

	public void setResponseBody(String responseBody) {
		this.responseBody = responseBody;
	}

	public Integer getResponseCode() {
		return responseCode;
	}

	public void setResponseCode(Integer responseCode) {
		this.responseCode = responseCode;
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

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		IdempotencyRecord that = (IdempotencyRecord) o;
		return Objects.equals(idempotencyKey, that.idempotencyKey);
	}

	@Override
	public int hashCode() {
		return Objects.hash(idempotencyKey);
	}

	public static class IdempotencyRecordBuilder {
		private String idempotencyKey;
		private String requestHash;
		private IdempotencyStatus status;
		private String responseBody;
		private Integer responseCode;

		public IdempotencyRecordBuilder idempotencyKey(String idempotencyKey) {
			this.idempotencyKey = idempotencyKey;
			return this;
		}

		public IdempotencyRecordBuilder requestHash(String requestHash) {
			this.requestHash = requestHash;
			return this;
		}

		public IdempotencyRecordBuilder status(IdempotencyStatus status) {
			this.status = status;
			return this;
		}

		public IdempotencyRecordBuilder responseBody(String responseBody) {
			this.responseBody = responseBody;
			return this;
		}

		public IdempotencyRecordBuilder responseCode(Integer responseCode) {
			this.responseCode = responseCode;
			return this;
		}

		public IdempotencyRecord build() {
			return new IdempotencyRecord(idempotencyKey, requestHash, status, responseBody, responseCode);
		}
	}
}
