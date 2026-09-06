package com.payflow.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.github.benmanes.caffeine.cache.Caffeine;

@Configuration
@EnableCaching
public class CacheConfig {

	private static final Logger LOG = LoggerFactory.getLogger(CacheConfig.class);

	public static final String CACHE_USERS = "users";
	public static final String CACHE_USER_LEDGERS = "user_ledgers";

	@Value("${payflow.cache.caffeine.spec:maximumSize=1000,expireAfterWrite=600s}")
	private String caffeineSpec;

	@Value("${payflow.cache.default-ttl-seconds:600}")
	private long defaultTtl;

	@Value("${payflow.cache.users-ttl-seconds:600}")
	private long usersTtl;

	@Value("${payflow.cache.user-ledgers-ttl-seconds:60}")
	private long ledgersTtl;

	@Bean
	@Profile("!prod")
	public CacheManager caffeineCacheManager() {
		LOG.info("Initializing Caffeine CacheManager with spec: {}", caffeineSpec);
		CaffeineCacheManager cacheManager = new CaffeineCacheManager();
		cacheManager.setCaffeine(Caffeine.from(caffeineSpec));
		cacheManager.setCacheNames(List.of(CACHE_USERS, CACHE_USER_LEDGERS));
		return cacheManager;
	}

	@Bean
	@Profile("prod")
	public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
		LOG.info("Redis CacheManager: defaultTTL={}s", defaultTtl);
		LOG.info("Cache TTL overrides: usersTTL={}s, ledgersTTL={}s", usersTtl, ledgersTtl);

		RedisSerializer<Object> jsonSerializer = RedisSerializer.json();

		SerializationPair<String> keyPair = SerializationPair.fromSerializer(new StringRedisSerializer());
		SerializationPair<Object> valuePair = SerializationPair.fromSerializer(jsonSerializer);

		RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig();
		defaultConfig = defaultConfig.entryTtl(Duration.ofSeconds(defaultTtl));
		defaultConfig = defaultConfig.disableCachingNullValues();
		defaultConfig = defaultConfig.serializeKeysWith(keyPair);
		defaultConfig = defaultConfig.serializeValuesWith(valuePair);

		Map<String, RedisCacheConfiguration> initialConfigs = new HashMap<>();
		initialConfigs.put(CACHE_USERS, defaultConfig.entryTtl(Duration.ofSeconds(usersTtl)));
		initialConfigs.put(CACHE_USER_LEDGERS, defaultConfig.entryTtl(Duration.ofSeconds(ledgersTtl)));

		return RedisCacheManager.builder(connectionFactory).cacheDefaults(defaultConfig)
				.withInitialCacheConfigurations(initialConfigs).build();
	}
}
