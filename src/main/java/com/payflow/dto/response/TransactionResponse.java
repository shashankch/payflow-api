package com.payflow.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.payflow.entity.Transaction;
import com.payflow.entity.TransactionStatus;
import com.payflow.entity.TransactionType;

public record TransactionResponse(Long transactionId, UUID referenceId, String senderUpiId, String receiverUpiId,
		BigDecimal amount, TransactionStatus status, TransactionType type, String note, Instant createdAt) {
	public static TransactionResponse fromEntity(Transaction tx) {
		Long id = tx.getTransactionId();
		UUID refId = tx.getReferenceId();
		String sender = tx.getSenderUpiId();
		String receiver = tx.getReceiverUpiId();
		BigDecimal amount = tx.getAmount();
		TransactionStatus status = tx.getStatus();
		TransactionType type = tx.getType();
		String note = tx.getNote();
		Instant created = tx.getCreatedAt();
		return new TransactionResponse(id, refId, sender, receiver, amount, status, type, note, created);
	}
}
