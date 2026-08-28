package com.payflow.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.payflow.dto.request.CreateUserRequest;
import com.payflow.entity.BalanceLedgerEntry;
import com.payflow.entity.User;
import com.payflow.exception.DuplicateUpiIdException;
import com.payflow.exception.ForbiddenOperationException;
import com.payflow.exception.UserNotFoundException;
import com.payflow.repository.BalanceLedgerRepository;
import com.payflow.repository.UserRepository;
import com.payflow.security.SecurityUtils;

@Service
public class UserService {

	private static final Logger LOG = LoggerFactory.getLogger(UserService.class);

	private final UserRepository userRepository;
	private final BalanceLedgerRepository balanceLedgerRepository;
	private final UpiValidationService upiValidationService;

	public UserService(UserRepository userRepository, //
			BalanceLedgerRepository balanceLedgerRepository, //
			UpiValidationService upiValidationService) {
		this.userRepository = userRepository;
		this.balanceLedgerRepository = balanceLedgerRepository;
		this.upiValidationService = upiValidationService;
	}

	@Transactional
	public User registerUser(CreateUserRequest request) {
		if (userRepository.findByUpiId(request.getUpiId()).isPresent()) {
			throw new DuplicateUpiIdException(request.getUpiId());
		}
		upiValidationService.validateUpi(request.getUpiId());
		LOG.info("Registering new user with UPI ID: {}", request.getUpiId());
		User user = User.builder().name(request.getName()).upiId(request.getUpiId())
				.phoneNumber(request.getPhoneNumber()).balance(request.getBalance()).build();
		User savedUser = userRepository.save(user);
		LOG.info("User registered: refId={}, upiId={}", savedUser != null ? savedUser.getReferenceId() : null,
				request.getUpiId());
		return savedUser;
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
	public User getUserByUpiId(String upiId) {
		return userRepository.findByUpiId(upiId)
				.orElseThrow(() -> new UserNotFoundException("User not found with UPI ID: " + upiId));
	}

	@Transactional(readOnly = true)
	public List<User> getUsersWithBalanceAbove(BigDecimal amount) {
		return userRepository.findUsersWithBalanceGreaterThan(amount);
	}

	@Transactional(readOnly = true)
	public Page<BalanceLedgerEntry> getUserLedger(UUID userReferenceId, Pageable pageable) {
		User user = userRepository.findByReferenceId(userReferenceId)
				.orElseThrow(() -> new UserNotFoundException("User not found: " + userReferenceId));

		String authenticatedUpi = SecurityUtils.getAuthenticatedUpiId();
		if (authenticatedUpi != null && !authenticatedUpi.equalsIgnoreCase(user.getUpiId())) {
			throw new ForbiddenOperationException("Authenticated user '" + authenticatedUpi
					+ "' is not authorized to view ledger for: " + userReferenceId);
		}

		return balanceLedgerRepository.findByUserReferenceIdOrderByCreatedAtDesc(userReferenceId, pageable);
	}

	@Transactional(readOnly = true)
	public Page<BalanceLedgerEntry> getUserLedgerByReferenceId(UUID userReferenceId, Pageable pageable) {
		return getUserLedger(userReferenceId, pageable);
	}
}
