package com.payflow.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class KafkaConfigTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(KafkaConfig.class);

	@Test
	@DisplayName("Should configure payflowTransfersTopic with 3 partitions and 1 replica in prod profile")
	void shouldConfigureTopic_inProdProfile() {
		contextRunner.withPropertyValues("spring.profiles.active=prod").run(context -> {
			assertThat(context).hasSingleBean(KafkaConfig.class);
			assertThat(context).hasSingleBean(NewTopic.class);
			NewTopic topic = context.getBean(NewTopic.class);
			assertThat(topic.name()).isEqualTo("payflow.transfers");
			assertThat(topic.numPartitions()).isEqualTo(3);
			assertThat(topic.replicationFactor()).isEqualTo((short) 1);
		});
	}

	@Test
	@DisplayName("Should configure payflowTransfersTopic with custom topic name when property provided")
	void shouldConfigureCustomTopicName_whenPropertySet() {
		contextRunner.withPropertyValues("spring.profiles.active=kafka",
				"payflow.kafka.transfers-topic=custom.payflow.transfers").run(context -> {
					assertThat(context).hasSingleBean(KafkaConfig.class);
					NewTopic topic = context.getBean(NewTopic.class);
					assertThat(topic.name()).isEqualTo("custom.payflow.transfers");
				});
	}

	@Test
	@DisplayName("Should not load KafkaConfig or NewTopic in test or local profiles")
	void shouldNotLoadKafkaConfig_inNonProdProfile() {
		contextRunner.withPropertyValues("spring.profiles.active=test").run(context -> {
			assertThat(context).doesNotHaveBean(KafkaConfig.class);
			assertThat(context).doesNotHaveBean(NewTopic.class);
		});

		contextRunner.withPropertyValues("spring.profiles.active=local").run(context -> {
			assertThat(context).doesNotHaveBean(KafkaConfig.class);
			assertThat(context).doesNotHaveBean(NewTopic.class);
		});
	}
}
