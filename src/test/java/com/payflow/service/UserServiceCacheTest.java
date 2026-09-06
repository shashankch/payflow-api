package com.payflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.payflow.config.CacheConfig;
import com.payflow.dto.request.CreateUserRequest;
import com.payflow.entity.BalanceLedgerEntry;
import com.payflow.entity.User;
import com.payflow.repository.BalanceLedgerRepository;
import com.payflow.repository.UserRepository;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {CacheConfig.class, UserService.class})
class UserServiceCacheTest {

	@MockitoBean
	private UserRepository userRepository;

	@MockitoBean
	private BalanceLedgerRepository balanceLedgerRepository;

	@MockitoBean
	private UpiValidationService upiValidationService;

	@Autowired
	private UserService userService;

	@Autowired
	private CacheManager cacheManager;

	private User sampleUser;
	private UUID sampleReferenceId;

	@BeforeEach
	void setUp() {
		Cache usersCache = cacheManager.getCache(CacheConfig.CACHE_USERS);
		if (usersCache != null) {
			usersCache.clear();
		}
		Cache ledgersCache = cacheManager.getCache(CacheConfig.CACHE_USER_LEDGERS);
		if (ledgersCache != null) {
			ledgersCache.clear();
		}

		sampleReferenceId = UUID.randomUUID();
		sampleUser = User.builder().userId(1L).referenceId(sampleReferenceId).name("Alice").upiId("alice@payflow")
				.phoneNumber("9876543210").balance(new BigDecimal("500.00")).build();
	}

	@Test
	@DisplayName("Should cache getUserByReferenceId result on subsequent invocations")
	void shouldCacheGetUserByReferenceId() {
		when(userRepository.findByReferenceId(sampleReferenceId)).thenReturn(Optional.of(sampleUser));

		Optional<User> firstCall = userService.getUserByReferenceId(sampleReferenceId);
		Optional<User> secondCall = userService.getUserByReferenceId(sampleReferenceId);

		assertThat(firstCall).isPresent().contains(sampleUser);
		assertThat(secondCall).isPresent().contains(sampleUser);
		verify(userRepository, times(1)).findByReferenceId(sampleReferenceId);
	}

	@Test
	@DisplayName("Should cache getUserByUpiId result on subsequent invocations")
	void shouldCacheGetUserByUpiId() {
		when(userRepository.findByUpiId("alice@payflow")).thenReturn(Optional.of(sampleUser));

		User firstCall = userService.getUserByUpiId("alice@payflow");
		User secondCall = userService.getUserByUpiId("alice@payflow");

		assertThat(firstCall).isEqualTo(sampleUser);
		assertThat(secondCall).isEqualTo(sampleUser);
		verify(userRepository, times(1)).findByUpiId("alice@payflow");
	}

	@Test
	@DisplayName("Should cache findByUpiId result on subsequent invocations")
	void shouldCacheFindByUpiId() {
		when(userRepository.findByUpiId("alice@payflow")).thenReturn(Optional.of(sampleUser));

		Optional<User> firstCall = userService.findByUpiId("alice@payflow");
		Optional<User> secondCall = userService.findByUpiId("alice@payflow");

		assertThat(firstCall).isPresent().contains(sampleUser);
		assertThat(secondCall).isPresent().contains(sampleUser);
		verify(userRepository, times(1)).findByUpiId("alice@payflow");
	}

	@Test
	@DisplayName("Should evict users and user_ledgers cache on registerUser")
	void shouldEvictCacheOnRegisterUser() {
		when(userRepository.findByReferenceId(sampleReferenceId)).thenReturn(Optional.of(sampleUser));

		// Populate cache
		userService.getUserByReferenceId(sampleReferenceId);
		verify(userRepository, times(1)).findByReferenceId(sampleReferenceId);

		// Trigger registerUser which has @CacheEvict
		when(userRepository.findByUpiId("bob@payflow")).thenReturn(Optional.empty());
		when(userRepository.save(any(User.class))).thenReturn(sampleUser);

		CreateUserRequest request = new CreateUserRequest("Bob", "bob@payflow", "9876543211", new BigDecimal("100.00"));
		userService.registerUser(request);

		// Next call should hit repository again
		userService.getUserByReferenceId(sampleReferenceId);
		verify(userRepository, times(2)).findByReferenceId(sampleReferenceId);
	}

	@Test
	@DisplayName("Should cache getUserLedger result on subsequent invocations")
	void shouldCacheGetUserLedger() {
		Pageable pageable = PageRequest.of(0, 10);
		Page<BalanceLedgerEntry> emptyPage = new PageImpl<>(java.util.List.of());

		when(userRepository.findByReferenceId(sampleReferenceId)).thenReturn(Optional.of(sampleUser));
		when(balanceLedgerRepository.findByUserReferenceIdOrderByCreatedAtDesc(sampleReferenceId, pageable))
				.thenReturn(emptyPage);

		Page<BalanceLedgerEntry> firstCall = userService.getUserLedger(sampleReferenceId, pageable);
		Page<BalanceLedgerEntry> secondCall = userService.getUserLedger(sampleReferenceId, pageable);

		assertThat(firstCall).isNotNull();
		assertThat(secondCall).isNotNull();
		verify(balanceLedgerRepository, times(1)).findByUserReferenceIdOrderByCreatedAtDesc(sampleReferenceId,
				pageable);
	}
}
