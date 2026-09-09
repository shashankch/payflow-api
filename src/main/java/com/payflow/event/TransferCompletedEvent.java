package com.payflow.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.modulith.events.Externalized;

import com.payflow.entity.TransactionStatus;

@Externalized
public record TransferCompletedEvent(UUID referenceId, String senderUpi, String receiverUpi, BigDecimal amount,
		TransactionStatus status, BigDecimal senderAfter, BigDecimal receiverAfter, Instant timestamp) {
}
