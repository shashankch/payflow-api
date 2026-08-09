package com.payflow.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.payflow.entity.LedgerEntryType;

public record LedgerEntryResponse(Long ledgerId, UUID userReferenceId, UUID transactionReferenceId,
		LedgerEntryType entryType, BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter,
		Instant createdAt) {
}
