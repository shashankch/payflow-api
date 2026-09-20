package com.payflow;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import com.payflow.security.JwtTokenProvider;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

	@ServiceConnection
	protected static final PostgreSQLContainer<?> POSTGRES_CONTAINER = new PostgreSQLContainer<>("postgres:16-alpine")
			.withDatabaseName("payflow_test_db").withUsername("test_user").withPassword("test_password");

	static {
		if (DockerClientFactory.instance().isDockerAvailable()) {
			POSTGRES_CONTAINER.start();
		}
	}

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url",
				() -> POSTGRES_CONTAINER.isRunning() ? POSTGRES_CONTAINER.getJdbcUrl() : "");
		registry.add("spring.datasource.username",
				() -> POSTGRES_CONTAINER.isRunning() ? POSTGRES_CONTAINER.getUsername() : "");
		registry.add("spring.datasource.password",
				() -> POSTGRES_CONTAINER.isRunning() ? POSTGRES_CONTAINER.getPassword() : "");
	}

	@Autowired(required = false)
	protected JwtTokenProvider jwtTokenProvider;

	protected HttpHeaders authHeaders(String upiId) {
		HttpHeaders headers = new HttpHeaders();
		if (jwtTokenProvider != null) {
			String token = jwtTokenProvider.generateToken(upiId, UUID.randomUUID());
			headers.setBearerAuth(token);
		}
		return headers;
	}
}
