package com.payflow.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Request payload for user authentication and JWT token issuance")
public class LoginRequest {

	@NotBlank(message = "UPI ID is required")
	@Size(max = 100, message = "UPI ID must not exceed 100 characters")
	@Pattern(regexp = "^[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z]{2,64}$", message = "Invalid UPI ID format")
	@Schema(description = "Unique UPI ID of the registered user", example = "alice@payflow")
	private String upiId;

	public LoginRequest() {
	}

	public LoginRequest(String upiId) {
		this.upiId = upiId;
	}

	public String getUpiId() {
		return upiId;
	}

	public void setUpiId(String upiId) {
		this.upiId = upiId;
	}
}
