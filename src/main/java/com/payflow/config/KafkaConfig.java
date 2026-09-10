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

	@Value("${payflow.kafka.transfers-topic:payflow.transfers}")
	private String transfersTopic;

	@Value("${payflow.kafka.topic-partitions:3}")
	private int topicPartitions;

	@Value("${payflow.kafka.topic-replicas:1}")
	private short topicReplicas;

	@Bean
	public NewTopic payflowTransfersTopic() {
		return TopicBuilder.name(transfersTopic).partitions(topicPartitions).replicas(topicReplicas).build();
	}

	@Bean
	public EventExternalizationConfiguration eventExternalizationConfiguration() {
		return EventExternalizationConfiguration.externalizing()
				.select(EventExternalizationConfiguration.annotatedAsExternalized())
				.route(TransferCompletedEvent.class, this::routeTransferEvent).build();
	}

	private RoutingTarget routeTransferEvent(TransferCompletedEvent event) {
		return RoutingTarget.forTarget(transfersTopic).andKey(event.senderUpi());
	}
}
