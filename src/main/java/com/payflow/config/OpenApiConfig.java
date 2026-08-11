package com.payflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI payflowOpenAPI() {
		return new OpenAPI().info(new Info().title("Payflow API")
				.description("Peer-to-peer payment and transaction ledger API").version("v0.4.0")
				.contact(new Contact().name("Payflow Engineering").email("shashankchandel@gmail.com"))
				.license(new License().name("MIT License").url("https://opensource.org/licenses/MIT")));
	}
}
