package com.payflow.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.test.util.ReflectionTestUtils;

class CacheConfigTest {

	private CacheConfig cacheConfig;

	@BeforeEach
	void setUp() {
		cacheConfig = new CacheConfig();
		ReflectionTestUtils.setField(cacheConfig, "caffeineSpec", "maximumSize=100,expireAfterWrite=60s");
		ReflectionTestUtils.setField(cacheConfig, "defaultTtl", 600L);
		ReflectionTestUtils.setField(cacheConfig, "usersTtl", 600L);
		ReflectionTestUtils.setField(cacheConfig, "ledgersTtl", 60L);
	}

	@Test
	@DisplayName("Should initialize CaffeineCacheManager with configured caches")
	void shouldInitializeCaffeineCacheManager() {
		CacheManager cacheManager = cacheConfig.caffeineCacheManager();

		assertThat(cacheManager).isNotNull();
		assertThat(cacheManager).isInstanceOf(CaffeineCacheManager.class);
		assertThat(cacheManager.getCacheNames()).containsExactlyInAnyOrder(CacheConfig.CACHE_USERS,
				CacheConfig.CACHE_USER_LEDGERS);
	}

	@Test
	@DisplayName("Should store and retrieve values from Caffeine cache")
	void shouldStoreAndRetrieveValuesFromCaffeineCache() {
		CacheManager cacheManager = cacheConfig.caffeineCacheManager();
		Cache usersCache = cacheManager.getCache(CacheConfig.CACHE_USERS);

		assertThat(usersCache).isNotNull();
		usersCache.put("testKey", "testValue");

		assertThat(usersCache.get("testKey", String.class)).isEqualTo("testValue");
	}

	@Test
	@DisplayName("Should evict values from Caffeine cache")
	void shouldEvictValuesFromCaffeineCache() {
		CacheManager cacheManager = cacheConfig.caffeineCacheManager();
		Cache usersCache = cacheManager.getCache(CacheConfig.CACHE_USERS);

		assertThat(usersCache).isNotNull();
		usersCache.put("evictKey", "evictValue");
		assertThat(usersCache.get("evictKey", String.class)).isEqualTo("evictValue");

		usersCache.evict("evictKey");
		assertThat(usersCache.get("evictKey")).isNull();
	}
}
