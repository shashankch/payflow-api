package com.payflow.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.payflow.dto.request.CreateUserRequest;
import com.payflow.entity.User;
import com.payflow.exception.DuplicateUpiIdException;
import com.payflow.repository.UserRepository;

@Service
public class UserService {

	private final UserRepository userRepository;

	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Transactional
	public User registerUser(CreateUserRequest request) {
		if (userRepository.findByUpiId(request.getUpiId()).isPresent()) {
			throw new DuplicateUpiIdException(request.getUpiId());
		}
		User user = User.builder().name(request.getName()).upiId(request.getUpiId())
				.phoneNumber(request.getPhoneNumber()).balance(request.getBalance()).build();
		return userRepository.save(user);
	}

	@Transactional(readOnly = true)
	public List<User> getAllUsers() {
		return userRepository.findAll();
	}

	@Transactional(readOnly = true)
	public Page<User> getAllUsers(Pageable pageable) {
		return userRepository.findAll(pageable);
	}

	@Transactional(readOnly = true)
	public Optional<User> getUserById(Long id) {
		return userRepository.findById(id);
	}

	@Transactional(readOnly = true)
	public Optional<User> getUserByReferenceId(UUID referenceId) {
		return userRepository.findByReferenceId(referenceId);
	}

	@Transactional(readOnly = true)
	public Optional<User> findByUpiId(String upiId) {
		return userRepository.findByUpiId(upiId);
	}

	@Transactional(readOnly = true)
	public List<User> getUsersWithBalanceAbove(BigDecimal amount) {
		return userRepository.findUsersWithBalanceGreaterThan(amount);
	}
}
