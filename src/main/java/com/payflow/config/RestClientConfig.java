package com.payflow.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import com.payflow.client.UpiValidationClient;

@Configuration
@EnableRetry
public class RestClientConfig {

	@Bean
	public UpiValidationClient upiValidationClient(RestClient.Builder restClientBuilder, //
			@Value("${payflow.upi-validation.base-url:http://localhost:9090}") String baseUrl, //
			@Value("${payflow.upi-validation.connect-timeout-ms:3000}") long connectTimeout, //
			@Value("${payflow.upi-validation.read-timeout-ms:5000}") long readTimeout) {

		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeout));
		requestFactory.setReadTimeout(Duration.ofMillis(readTimeout));

		RestClient restClient = restClientBuilder.baseUrl(baseUrl).requestFactory(requestFactory).build();

		RestClientAdapter adapter = RestClientAdapter.create(restClient);
		HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();

		return factory.createClient(UpiValidationClient.class);
	}
}
