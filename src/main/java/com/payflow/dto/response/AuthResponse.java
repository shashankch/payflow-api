package com.payflow.dto.response;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Authentication response containing JWT access token and user metadata")
public record AuthResponse(@Schema(description = "Signed JWT access token") String accessToken,

		@Schema(description = "Token type scheme", example = "Bearer") String tokenType,

		@Schema(description = "Token time-to-live in seconds", example = "3600") Long expiresIn,

		@Schema(description = "Authenticated user UPI ID", example = "alice@payflow") String upiId,

		@Schema(description = "User reference ID") UUID referenceId) {
}
