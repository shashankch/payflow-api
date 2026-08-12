package com.payflow;

import org.flywaydb.core.Flyway;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {

	@Container
	@ServiceConnection
	protected static final PostgreSQLContainer<?> POSTGRES_CONTAINER = new PostgreSQLContainer<>("postgres:16-alpine")
			.withDatabaseName("payflow_test_db").withUsername("test_user").withPassword("test_password");

	@DynamicPropertySource
	static void migrateFlywaySchema(DynamicPropertyRegistry registry) {
		if (POSTGRES_CONTAINER.isRunning()) {
			Flyway.configure()
					.dataSource(POSTGRES_CONTAINER.getJdbcUrl(), POSTGRES_CONTAINER.getUsername(),
							POSTGRES_CONTAINER.getPassword())
					.locations("classpath:db/migration").baselineOnMigrate(true).load().migrate();
		}
	}
}
