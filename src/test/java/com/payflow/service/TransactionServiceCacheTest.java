package com.payflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.payflow.config.CacheConfig;
import com.payflow.config.MetricsConfig;
import com.payflow.config.RateLimiterKeyResolver;
import com.payflow.dto.request.TransferMoneyRequest;
import com.payflow.entity.BalanceLedgerEntry;
import com.payflow.entity.Transaction;
import com.payflow.entity.TransactionStatus;
import com.payflow.entity.TransactionType;
import com.payflow.entity.User;
import com.payflow.repository.BalanceLedgerRepository;
import com.payflow.repository.TransactionRepository;
import com.payflow.repository.UserRepository;
import com.payflow.resilience.UserRateLimiterService;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {CacheConfig.class, TransactionService.class})
class TransactionServiceCacheTest {

	@MockitoBean
	private TransactionRepository transactionRepository;

	@MockitoBean
	private UserRepository userRepository;

	@MockitoBean
	private BalanceLedgerRepository balanceLedgerRepository;

	@MockitoBean
	private ApplicationEventPublisher eventPublisher;

	@MockitoBean
	private MetricsConfig metricsConfig;

	@MockitoBean
	private RateLimiterKeyResolver rateLimiterKeyResolver;

	@MockitoBean
	private UserRateLimiterService userRateLimiterService;

	@Autowired
	private TransactionService transactionService;

	@Autowired
	private CacheManager cacheManager;

	private User sender;
	private User receiver;

	@BeforeEach
	void setUp() {
		sender = User.builder().userId(1L).referenceId(UUID.randomUUID()).name("Alice").upiId("alice@payflow")
				.phoneNumber("9876543210").balance(new BigDecimal("1000.00")).build();

		receiver = User.builder().userId(2L).referenceId(UUID.randomUUID()).name("Bob").upiId("bob@payflow")
				.phoneNumber("9876543211").balance(new BigDecimal("500.00")).build();
	}

	@Test
	@DisplayName("Should evict users and user_ledgers caches upon successful money transfer")
	void shouldEvictCachesOnSendMoney() {
		Cache usersCache = cacheManager.getCache(CacheConfig.CACHE_USERS);
		Cache ledgersCache = cacheManager.getCache(CacheConfig.CACHE_USER_LEDGERS);

		assertThat(usersCache).isNotNull();
		assertThat(ledgersCache).isNotNull();

		usersCache.put("upi:alice@payflow", sender);
		usersCache.put("upi:bob@payflow", receiver);
		ledgersCache.put("alice_ledger_0", "dummyData");

		assertThat(usersCache.get("upi:alice@payflow")).isNotNull();
		assertThat(ledgersCache.get("alice_ledger_0")).isNotNull();

		when(userRepository.findByUpiIdWithLock("alice@payflow")).thenReturn(Optional.of(sender));
		when(userRepository.findByUpiIdWithLock("bob@payflow")).thenReturn(Optional.of(receiver));
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Transaction tx = Transaction.builder().transactionId(1L).referenceId(UUID.randomUUID()).sender(sender)
				.receiver(receiver).senderUpiId("alice@payflow").receiverUpiId("bob@payflow")
				.amount(new BigDecimal("100.00")).status(TransactionStatus.COMPLETED).type(TransactionType.TRANSFER)
				.build();

		when(transactionRepository.save(any(Transaction.class))).thenReturn(tx);
		when(balanceLedgerRepository.save(any(BalanceLedgerEntry.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		TransferMoneyRequest request = new TransferMoneyRequest("alice@payflow", "bob@payflow",
				new BigDecimal("100.00"), "Rent");

		transactionService.sendMoney(request);

		assertThat(usersCache.get("upi:alice@payflow")).isNull();
		assertThat(usersCache.get("upi:bob@payflow")).isNull();
		assertThat(ledgersCache.get("alice_ledger_0")).isNull();
	}
}
