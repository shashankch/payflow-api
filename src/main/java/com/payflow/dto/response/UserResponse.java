package com.payflow.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

import com.payflow.entity.User;

public record UserResponse(Long userId, String name, String upiId, BigDecimal balance, String phoneNumber,
		Instant createdAt, Instant updatedAt) {
	public static UserResponse fromEntity(User user) {
		return new UserResponse(user.getUserId(), user.getName(), user.getUpiId(), user.getBalance(),
				user.getPhoneNumber(), user.getCreatedAt(), user.getUpdatedAt());
	}
}
