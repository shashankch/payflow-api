package com.payflow.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.payflow.entity.TransactionStatus;
import com.payflow.entity.TransactionType;

public record TransactionResponse(Long transactionId, UUID referenceId, String senderUpiId, String receiverUpiId,
		BigDecimal amount, TransactionStatus status, TransactionType type, String note, Instant createdAt) {
}
