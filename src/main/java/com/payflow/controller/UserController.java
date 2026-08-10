package com.payflow.controller;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.payflow.dto.request.CreateUserRequest;
import com.payflow.dto.response.LedgerEntryResponse;
import com.payflow.dto.response.PagedResponse;
import com.payflow.dto.response.UserResponse;
import com.payflow.entity.BalanceLedgerEntry;
import com.payflow.entity.User;
import com.payflow.exception.UserNotFoundException;
import com.payflow.mapper.LedgerMapper;
import com.payflow.mapper.UserMapper;
import com.payflow.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/v1/users")
@Validated
@Tag(name = "User Management", description = "Endpoints for registering and querying user accounts")
public class UserController {

	private final UserService userService;
	private final UserMapper userMapper;
	private final LedgerMapper ledgerMapper;

	public UserController(UserService userService, UserMapper userMapper, LedgerMapper ledgerMapper) {
		this.userService = userService;
		this.userMapper = userMapper;
		this.ledgerMapper = ledgerMapper;
	}

	@PostMapping
	@Operation(summary = "Register a new user", description = "Creates a new user profile with "
			+ "initial balance and unique UPI ID")
	@ApiResponse(responseCode = "201", description = "User successfully registered")
	@ApiResponse(responseCode = "400", description = "Invalid request payload or constraint validation error")
	public ResponseEntity<UserResponse> registerUser(@Valid @RequestBody CreateUserRequest request) {
		User createdUser = userService.registerUser(request);
		UserResponse response = userMapper.toResponse(createdUser);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
				.buildAndExpand(createdUser.getReferenceId()).toUri();
		return ResponseEntity.created(location).body(response);
	}

	@GetMapping
	@Operation(summary = "Get paginated users", description = "Retrieves a paginated list of "
			+ "registered users sorted by specified attribute")
	@ApiResponse(responseCode = "200", description = "Paginated users list retrieved successfully")
	public ResponseEntity<PagedResponse<UserResponse>> getUsers(@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
			@RequestParam(defaultValue = "userId") String sortBy) {
		Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy).ascending());
		Page<UserResponse> userPage = userService.getAllUsers(pageable).map(userMapper::toResponse);
		return ResponseEntity.ok(PagedResponse.fromPage(userPage));
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get user by reference ID", description = "Fetches user details by UUID reference ID")
	@ApiResponse(responseCode = "200", description = "User found and returned")
	@ApiResponse(responseCode = "404", description = "User not found")
	public ResponseEntity<UserResponse> getUserById(@PathVariable UUID id) {
		User user = userService.getUserByReferenceId(id)
				.orElseThrow(() -> new UserNotFoundException("User not found: " + id));
		return ResponseEntity.ok(userMapper.toResponse(user));
	}

	@GetMapping("/upi/{upiId}")
	@Operation(summary = "Get user by UPI ID", description = "Fetches details of a user by their unique UPI ID")
	@ApiResponse(responseCode = "200", description = "User found and returned")
	@ApiResponse(responseCode = "404", description = "User not found")
	public ResponseEntity<UserResponse> getUserByUpiId(@PathVariable String upiId) {
		User user = userService.findByUpiId(upiId)
				.orElseThrow(() -> new UserNotFoundException("User not found with UPI ID: " + upiId));
		return ResponseEntity.ok(userMapper.toResponse(user));
	}

	@GetMapping("/balance/{amount}")
	@Operation(summary = "Get users by balance threshold", description = "Fetches users whose balance "
			+ "exceeds minimum threshold")
	@ApiResponse(responseCode = "200", description = "Matching users list returned")
	public ResponseEntity<List<UserResponse>> getUsersWithBalanceAbove(@PathVariable BigDecimal amount) {
		List<User> entityList = userService.getUsersWithBalanceAbove(amount);
		List<UserResponse> users = entityList.stream().map(userMapper::toResponse).toList();
		return ResponseEntity.ok(users);
	}

	@GetMapping("/{id}/ledger")
	@Operation(summary = "Get user balance ledger history", description = "Retrieves paginated double-entry "
			+ "balance ledger audit entries for a user by UUID reference ID")
	@ApiResponse(responseCode = "200", description = "Paginated ledger history returned")
	@ApiResponse(responseCode = "404", description = "User not found")
	public ResponseEntity<PagedResponse<LedgerEntryResponse>> getUserLedger(@PathVariable UUID id,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
		Pageable pageable = PageRequest.of(page, size);
		Page<BalanceLedgerEntry> entries = userService.getUserLedger(id, pageable);
		Page<LedgerEntryResponse> ledgerPage = entries.map(ledgerMapper::toResponse);
		return ResponseEntity.ok(PagedResponse.fromPage(ledgerPage));
	}
}
