package com.payflow.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.modulith.events.EventExternalizationConfiguration;

class KafkaConfigTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(KafkaConfig.class);

	@Test
	@DisplayName("Should configure payflowTransfersTopic and EventExternalizationConfiguration in prod profile")
	void shouldConfigureTopicAndExternalization_inProdProfile() {
		contextRunner.withPropertyValues("spring.profiles.active=prod", "payflow.kafka.topic-replicas=3")
				.run(context -> {
					assertThat(context).hasSingleBean(KafkaConfig.class);
					assertThat(context).hasSingleBean(NewTopic.class);
					assertThat(context).hasSingleBean(EventExternalizationConfiguration.class);

					NewTopic topic = context.getBean(NewTopic.class);
					assertThat(topic.name()).isEqualTo("payflow.transfers");
					assertThat(topic.numPartitions()).isEqualTo(3);
					assertThat(topic.replicationFactor()).isEqualTo((short) 3);
				});
	}

	@Test
	@DisplayName("Should configure custom topic, partitions, and replicas when properties provided")
	void shouldConfigureCustomTopicSettings_whenPropertiesProvided() {
		contextRunner.withPropertyValues("spring.profiles.active=kafka",
				"payflow.kafka.transfers-topic=custom.payflow.transfers", "payflow.kafka.topic-partitions=5",
				"payflow.kafka.topic-replicas=2").run(context -> {
					assertThat(context).hasSingleBean(KafkaConfig.class);
					assertThat(context).hasSingleBean(NewTopic.class);
					assertThat(context).hasSingleBean(EventExternalizationConfiguration.class);

					NewTopic topic = context.getBean(NewTopic.class);
					assertThat(topic.name()).isEqualTo("custom.payflow.transfers");
					assertThat(topic.numPartitions()).isEqualTo(5);
					assertThat(topic.replicationFactor()).isEqualTo((short) 2);
				});
	}

	@Test
	@DisplayName("Should not load KafkaConfig, NewTopic, or EventExternalizationConfiguration in test or local profiles")
	void shouldNotLoadKafkaConfig_inNonProdProfile() {
		contextRunner.withPropertyValues("spring.profiles.active=test").run(context -> {
			assertThat(context).doesNotHaveBean(KafkaConfig.class);
			assertThat(context).doesNotHaveBean(NewTopic.class);
			assertThat(context).doesNotHaveBean(EventExternalizationConfiguration.class);
		});

		contextRunner.withPropertyValues("spring.profiles.active=local").run(context -> {
			assertThat(context).doesNotHaveBean(KafkaConfig.class);
			assertThat(context).doesNotHaveBean(NewTopic.class);
			assertThat(context).doesNotHaveBean(EventExternalizationConfiguration.class);
		});
	}
}
