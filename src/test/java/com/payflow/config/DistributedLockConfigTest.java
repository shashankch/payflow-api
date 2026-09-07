package com.payflow.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.payflow.service.DistributedLockService;
import com.payflow.service.NoOpDistributedLockService;
import com.payflow.service.RedissonDistributedLockService;

class DistributedLockConfigTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withUserConfiguration(
			DistributedLockConfig.class, NoOpDistributedLockService.class, RedissonDistributedLockService.class);

	@Test
	@DisplayName("Should configure NoOpDistributedLockService and no RedissonClient in test profile")
	void shouldConfigureNoOpLockService_inTestProfile() {
		contextRunner.withPropertyValues("spring.profiles.active=test").run(context -> {
			assertThat(context).hasSingleBean(DistributedLockService.class);
			assertThat(context).hasSingleBean(NoOpDistributedLockService.class);
			assertThat(context).doesNotHaveBean(RedissonClient.class);
			assertThat(context).doesNotHaveBean(RedissonDistributedLockService.class);
		});
	}

	@Test
	@DisplayName("Should configure NoOpDistributedLockService in default/local profile")
	void shouldConfigureNoOpLockService_inDefaultProfile() {
		contextRunner.run(context -> {
			assertThat(context).hasSingleBean(DistributedLockService.class);
			assertThat(context).hasSingleBean(NoOpDistributedLockService.class);
			assertThat(context).doesNotHaveBean(RedissonClient.class);
		});
	}
}
