package com.payflow.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID referenceId, String name, String upiId, BigDecimal balance, String phoneNumber,
		Instant createdAt, Instant updatedAt) {
}
