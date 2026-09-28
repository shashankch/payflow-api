package com.payflow.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

	@Bean
	@ConditionalOnBean(ChatClient.Builder.class)
	public ChatClient chatClient(ChatClient.Builder builder) {
		String sys = "You are an expert personal finance and spend categorization assistant for Payflow. " //
				+ "Analyze transaction details and output structured financial insights.";
		return builder.defaultSystem(sys).build();
	}
}
