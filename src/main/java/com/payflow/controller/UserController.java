package com.payflow.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.payflow.entity.User;
import com.payflow.service.UserService;

@RestController
@RequestMapping("/users")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@PostMapping
	public User registerUser(@RequestBody User user) {
		return userService.registerUser(user);
	}

	@GetMapping
	public List<User> getAllUsers() {
		return userService.getAllUsers();
	}

	@GetMapping("/{id}")
	public Optional<User> getUserById(@PathVariable Long id) {
		return userService.getUserById(id);
	}

	@GetMapping("/upi/{upiId}")
	public Optional<User> getUserByUpiId(@PathVariable String upiId) {
		return userService.findByUpiId(upiId);
	}

	@GetMapping("/balance/{amount}")
	public List<User> getUsersWithBalanceAbove(@PathVariable BigDecimal amount) {
		return userService.getUsersWithBalanceAbove(amount);
	}
}
