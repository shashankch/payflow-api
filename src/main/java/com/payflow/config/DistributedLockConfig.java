package com.payflow.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Production configuration for Redisson distributed lock manager.
 */
@Configuration
@Profile("prod")
public class DistributedLockConfig {

	private static final Logger LOG = LoggerFactory.getLogger(DistributedLockConfig.class);

	@Value("${spring.data.redis.host:localhost}")
	private String redisHost;

	@Value("${spring.data.redis.port:6379}")
	private int redisPort;

	@Value("${spring.data.redis.password:}")
	private String redisPassword;

	@Value("${spring.data.redis.ssl.enabled:false}")
	private boolean sslEnabled;

	@Bean(destroyMethod = "shutdown")
	public RedissonClient redissonClient() {
		String prefix = sslEnabled ? "rediss://" : "redis://";
		String address = prefix + redisHost + ":" + redisPort;
		LOG.info("Configuring RedissonClient connected to {}", address);

		Config config = new Config();
		SingleServerConfig singleServer = config.useSingleServer().setAddress(address).setConnectionPoolSize(20)
				.setConnectionMinimumIdleSize(5).setTimeout(3000);

		if (redisPassword != null && !redisPassword.isBlank()) {
			singleServer.setPassword(redisPassword);
		}

		return Redisson.create(config);
	}
}
