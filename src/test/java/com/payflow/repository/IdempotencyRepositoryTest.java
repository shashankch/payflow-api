package com.payflow.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import com.payflow.entity.IdempotencyRecord;
import com.payflow.entity.IdempotencyStatus;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class IdempotencyRepositoryTest {

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private IdempotencyRepository idempotencyRepository;

	@Test
	@DisplayName("Should find record by idempotency key")
	void shouldFindByIdempotencyKey() {
		IdempotencyRecord record = new IdempotencyRecord("key-123", "hash123", IdempotencyStatus.SUCCESS,
				"{\"status\":\"SUCCESS\"}", 200);
		entityManager.persistAndFlush(record);

		Optional<IdempotencyRecord> found = idempotencyRepository.findById("key-123");

		assertThat(found).isPresent();
		assertThat(found.get().getRequestHash()).isEqualTo("hash123");
		assertThat(found.get().getStatus()).isEqualTo(IdempotencyStatus.SUCCESS);
		assertThat(found.get().getResponseCode()).isEqualTo(200);
	}

	@Test
	@DisplayName("Should delete records older than cutoff timestamp")
	void shouldDeleteRecordsOlderThanCutoff() {
		IdempotencyRecord recentRecord = new IdempotencyRecord("key-recent", "hash-recent", IdempotencyStatus.SUCCESS,
				"{}", 200);
		entityManager.persistAndFlush(recentRecord);

		Instant oldTimestamp = Instant.now().minus(Duration.ofDays(2));
		entityManager.getEntityManager().createNativeQuery(
				"INSERT INTO idempotency_registry (idempotency_key, request_hash, status, response_body, response_code, created_at, updated_at) "
						+ "VALUES ('key-old', 'hash-old', 'SUCCESS', '{}', 200, :oldTime, :oldTime)")
				.setParameter("oldTime", oldTimestamp).executeUpdate();

		Instant cutoff = Instant.now().minus(Duration.ofDays(1));
		int deletedCount = idempotencyRepository.deleteRecordsOlderThan(cutoff);

		assertThat(deletedCount).isEqualTo(1);
		assertThat(idempotencyRepository.findById("key-old")).isEmpty();
		assertThat(idempotencyRepository.findById("key-recent")).isPresent();
	}
}
