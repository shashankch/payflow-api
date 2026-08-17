package com.payflow.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.payflow.entity.TransactionStatus;

public record TransferCompletedEvent(UUID referenceId, String senderUpi, String receiverUpi, BigDecimal amount,
		TransactionStatus status, BigDecimal senderAfter, BigDecimal receiverAfter, Instant timestamp) {
}
