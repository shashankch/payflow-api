package com.payflow.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import com.payflow.AbstractIntegrationTest;

class PostgreSQLContainerIT extends AbstractIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("Should boot application with Testcontainers PostgreSQL and verify Flyway schema migration")
	void shouldBootApplication_withTestcontainersPostgreSQL() {
		assertThat(POSTGRES_CONTAINER.isRunning()).isTrue();
		assertThat(restTemplate).isNotNull();

		Integer userTableCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'users'", Integer.class);
		Integer flywayVersionCount = jdbcTemplate
				.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success = true", Integer.class);

		assertThat(userTableCount).isEqualTo(1);
		assertThat(flywayVersionCount).isGreaterThanOrEqualTo(6);
	}
}
