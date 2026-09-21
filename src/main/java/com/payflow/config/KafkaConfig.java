package com.payflow.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.RoutingTarget;

import com.payflow.event.TransferCompletedEvent;

@Configuration
@Profile({"prod", "kafka"})
public class KafkaConfig {

	@Bean
	public NewTopic payflowTransfersTopic(@Value("${payflow.kafka.transfers-topic:payflow.transfers}") String topic,
			@Value("${payflow.kafka.topic-partitions:3}") int partitions,
			@Value("${payflow.kafka.topic-replicas:1}") short replicas) {
		return TopicBuilder.name(topic).partitions(partitions).replicas(replicas).build();
	}

	@Bean
	public EventExternalizationConfiguration eventExternalizationConfiguration(
			@Value("${payflow.kafka.transfers-topic:payflow.transfers}") String topic) {
		return EventExternalizationConfiguration.externalizing()
				.select(EventExternalizationConfiguration.annotatedAsExternalized())
				.route(TransferCompletedEvent.class, it -> routeTransfer(it, topic)).build();
	}

	private RoutingTarget routeTransfer(TransferCompletedEvent it, String topic) {
		return RoutingTarget.forTarget(topic).andKey(it.senderUpi());
	}
}
