package com.payflow.controller;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

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
import com.payflow.dto.response.PagedResponse;
import com.payflow.dto.response.UserResponse;
import com.payflow.entity.User;
import com.payflow.service.UserService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/v1/users")
@Validated
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@PostMapping
	public ResponseEntity<UserResponse> registerUser(@Valid @RequestBody CreateUserRequest request) {
		User createdUser = userService.registerUser(request);
		UserResponse response = UserResponse.fromEntity(createdUser);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
				.buildAndExpand(createdUser.getUserId()).toUri();
		return ResponseEntity.created(location).body(response);
	}

	@GetMapping
	public ResponseEntity<PagedResponse<UserResponse>> getUsers(@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
			@RequestParam(defaultValue = "userId") String sortBy) {
		Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy).ascending());
		Page<UserResponse> userPage = userService.getAllUsers(pageable).map(UserResponse::fromEntity);
		return ResponseEntity.ok(PagedResponse.fromPage(userPage));
	}

	@GetMapping("/{id}")
	public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
		return userService.getUserById(id).map(UserResponse::fromEntity).map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@GetMapping("/upi/{upiId}")
	public ResponseEntity<UserResponse> getUserByUpiId(@PathVariable String upiId) {
		return userService.findByUpiId(upiId).map(UserResponse::fromEntity).map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@GetMapping("/balance/{amount}")
	public ResponseEntity<List<UserResponse>> getUsersWithBalanceAbove(@PathVariable BigDecimal amount) {
		List<User> entityList = userService.getUsersWithBalanceAbove(amount);
		List<UserResponse> users = entityList.stream().map(UserResponse::fromEntity).toList();
		return ResponseEntity.ok(users);
	}
}
