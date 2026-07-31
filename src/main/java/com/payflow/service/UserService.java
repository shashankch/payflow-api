package com.payflow.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.payflow.entity.User;
import com.payflow.repository.UserRepository;

@Service
public class UserService {

	private final UserRepository userRepository;

	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	public User registerUser(User user) {
		return userRepository.save(user);
	}

	public List<User> getAllUsers() {
		return userRepository.findAll();
	}

	public Optional<User> getUserById(Long id) {
		return userRepository.findById(id);
	}

	public Optional<User> findByUpiId(String upiId) {
		return userRepository.findByUpiId(upiId);
	}

	public List<User> getUsersWithBalanceAbove(BigDecimal amount) {
		return userRepository.findUsersWithBalanceGreaterThan(amount);
	}
}
