package com.payflow;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.payflow.security.JwtTokenProvider;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {

	@Container
	@ServiceConnection
	protected static final PostgreSQLContainer<?> POSTGRES_CONTAINER = new PostgreSQLContainer<>("postgres:16-alpine")
			.withDatabaseName("payflow_test_db").withUsername("test_user").withPassword("test_password");

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
