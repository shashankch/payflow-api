package com.payflow.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@Profile({"prod", "kafka"})
public class KafkaConfig {

	@Bean
	public NewTopic payflowTransfersTopic(
			@Value("${payflow.kafka.transfers-topic:payflow.transfers}") String topicName) {
		return TopicBuilder.name(topicName).partitions(3).replicas(1).build();
	}
}
