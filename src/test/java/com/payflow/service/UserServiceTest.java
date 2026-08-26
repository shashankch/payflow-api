package com.payflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.payflow.dto.request.CreateUserRequest;
import com.payflow.entity.BalanceLedgerEntry;
import com.payflow.entity.LedgerEntryType;
import com.payflow.entity.User;
import com.payflow.exception.DuplicateUpiIdException;
import com.payflow.exception.ForbiddenOperationException;
import com.payflow.exception.InvalidUpiException;
import com.payflow.exception.UserNotFoundException;
import com.payflow.repository.BalanceLedgerRepository;
import com.payflow.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private BalanceLedgerRepository balanceLedgerRepository;

	@Mock
	private UpiValidationService upiValidationService;

	@InjectMocks
	private UserService userService;

	private User sampleUser;
	private UUID sampleReferenceId;

	@BeforeEach
	void setUp() {
		sampleReferenceId = UUID.randomUUID();
		sampleUser = User.builder().userId(1L).referenceId(sampleReferenceId).name("Alice Johnson").upiId("alice@upi")
				.phoneNumber("9876543210").balance(new BigDecimal("1000.0000")).version(0L).createdAt(Instant.now())
				.updatedAt(Instant.now()).build();
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("Should register new user successfully when valid input provided")
	void shouldRegisterUser_whenValidInput() {
		CreateUserRequest request = new CreateUserRequest("Alice Johnson", "alice@upi", "9876543210",
				new BigDecimal("1000.0000"));

		when(userRepository.findByUpiId("alice@upi")).thenReturn(Optional.empty());
		when(userRepository.save(any(User.class))).thenReturn(sampleUser);

		User result = userService.registerUser(request);

		assertThat(result).isNotNull();
		assertThat(result.getReferenceId()).isEqualTo(sampleReferenceId);
		assertThat(result.getUpiId()).isEqualTo("alice@upi");
		assertThat(result.getBalance()).isEqualByComparingTo("1000.0000");
		verify(upiValidationService).validateUpi("alice@upi");
		verify(userRepository).save(any(User.class));
	}

	@Test
	@DisplayName("Should throw DuplicateUpiIdException when UPI ID already exists")
	void shouldThrowDuplicateUpi_whenUpiAlreadyExists() {
		CreateUserRequest request = new CreateUserRequest("Alice Johnson", "alice@upi", "9876543210",
				new BigDecimal("1000.0000"));

		when(userRepository.findByUpiId("alice@upi")).thenReturn(Optional.of(sampleUser));

		assertThatThrownBy(() -> userService.registerUser(request)).isInstanceOf(DuplicateUpiIdException.class)
				.hasMessageContaining("alice@upi");
		verifyNoInteractions(upiValidationService);
	}

	@Test
	@DisplayName("Should throw InvalidUpiException when external validation fails")
	void shouldThrowInvalidUpi_whenExternalValidationFails() {
		CreateUserRequest request = new CreateUserRequest("Alice Johnson", "alice@upi", "9876543210",
				new BigDecimal("1000.0000"));

		when(userRepository.findByUpiId("alice@upi")).thenReturn(Optional.empty());
		doThrow(new InvalidUpiException("UPI ID is invalid: alice@upi")).when(upiValidationService)
				.validateUpi("alice@upi");

		assertThatThrownBy(() -> userService.registerUser(request)).isInstanceOf(InvalidUpiException.class)
				.hasMessageContaining("alice@upi");
		verify(userRepository, never()).save(any(User.class));
	}

	@Test
	@DisplayName("Should return user when user found by reference UUID")
	void shouldReturnUser_whenGetUserByReferenceIdCalled() {
		when(userRepository.findByReferenceId(sampleReferenceId)).thenReturn(Optional.of(sampleUser));

		Optional<User> result = userService.getUserByReferenceId(sampleReferenceId);

		assertThat(result).isPresent();
		assertThat(result.get().getReferenceId()).isEqualTo(sampleReferenceId);
		assertThat(result.get().getName()).isEqualTo("Alice Johnson");
	}

	@Test
	@DisplayName("Should return empty optional when user reference ID does not exist")
	void shouldReturnEmptyOptional_whenUserIdNotExists() {
		UUID nonExistentId = UUID.randomUUID();
		when(userRepository.findByReferenceId(nonExistentId)).thenReturn(Optional.empty());

		Optional<User> result = userService.getUserByReferenceId(nonExistentId);

		assertThat(result).isEmpty();
	}

	@Test
	@DisplayName("Should return paginated users list when getAllUsers is called")
	void shouldReturnPaginatedUsers_whenGetAllUsersCalled() {
		Pageable pageable = PageRequest.of(0, 10);
		Page<User> userPage = new PageImpl<>(List.of(sampleUser), pageable, 1);

		when(userRepository.findAll(pageable)).thenReturn(userPage);

		Page<User> result = userService.getAllUsers(pageable);

		assertThat(result).isNotNull();
		assertThat(result.getTotalElements()).isEqualTo(1);
		assertThat(result.getContent().get(0).getReferenceId()).isEqualTo(sampleReferenceId);
	}

	@Test
	@DisplayName("Should return user ledger when authenticated user matches target user")
	void shouldReturnUserLedger_whenAuthorized() {
		SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken("alice@upi", null, List.of()));

		Pageable pageable = PageRequest.of(0, 10);
		BalanceLedgerEntry entry = BalanceLedgerEntry.builder().ledgerId(1L).user(sampleUser)
				.entryType(LedgerEntryType.CREDIT).amount(new BigDecimal("100.00")).build();
		Page<BalanceLedgerEntry> ledgerPage = new PageImpl<>(List.of(entry), pageable, 1);

		when(userRepository.findByReferenceId(sampleReferenceId)).thenReturn(Optional.of(sampleUser));
		when(balanceLedgerRepository.findByUserReferenceIdOrderByCreatedAtDesc(sampleReferenceId, pageable))
				.thenReturn(ledgerPage);

		Page<BalanceLedgerEntry> result = userService.getUserLedger(sampleReferenceId, pageable);

		assertThat(result).isNotNull();
		assertThat(result.getTotalElements()).isEqualTo(1);
	}

	@Test
	@DisplayName("Should throw ForbiddenOperationException when user attempts to view another user's ledger")
	void shouldThrowForbidden_whenUnauthorizedLedgerAccess() {
		SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken("mallory@upi", null, List.of()));

		Pageable pageable = PageRequest.of(0, 10);
		when(userRepository.findByReferenceId(sampleReferenceId)).thenReturn(Optional.of(sampleUser));

		assertThatThrownBy(() -> userService.getUserLedger(sampleReferenceId, pageable))
				.isInstanceOf(ForbiddenOperationException.class).hasMessageContaining("not authorized to view ledger");
	}

	@Test
	@DisplayName("Should throw UserNotFoundException when ledger requested for non-existent user")
	void shouldThrowUserNotFound_whenLedgerRequestedForMissingUser() {
		UUID missingId = UUID.randomUUID();
		Pageable pageable = PageRequest.of(0, 10);
		when(userRepository.findByReferenceId(missingId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.getUserLedger(missingId, pageable))
				.isInstanceOf(UserNotFoundException.class);
	}
}
