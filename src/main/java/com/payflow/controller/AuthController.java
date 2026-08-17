package com.payflow.controller;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.payflow.dto.request.LoginRequest;
import com.payflow.dto.response.AuthResponse;
import com.payflow.entity.User;
import com.payflow.security.JwtTokenProvider;
import com.payflow.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Endpoints for user authentication and JWT token issuance")
public class AuthController {

	private static final Logger LOG = LoggerFactory.getLogger(AuthController.class);

	private final UserService userService;
	private final JwtTokenProvider jwtTokenProvider;

	public AuthController(UserService userService, JwtTokenProvider jwtTokenProvider) {
		this.userService = userService;
		this.jwtTokenProvider = jwtTokenProvider;
	}

	@PostMapping("/login")
	@Operation(summary = "Authenticate user and issue JWT token")
	@ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Authentication successful"),
			@ApiResponse(responseCode = "400", description = "Invalid request payload"),
			@ApiResponse(responseCode = "404", description = "User not found"),
			@ApiResponse(responseCode = "422", description = "Validation failure")})
	public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
		LOG.info("Authenticating login request for upiId={}", request.getUpiId());

		User user = userService.getUserByUpiId(request.getUpiId());
		String token = jwtTokenProvider.generateToken(user.getUpiId(), user.getReferenceId());
		long expiresIn = jwtTokenProvider.getExpirationSeconds();

		String upi = user.getUpiId();
		UUID refId = user.getReferenceId();
		AuthResponse response = new AuthResponse(token, "Bearer", expiresIn, upi, refId);
		return ResponseEntity.ok(response);
	}
}
