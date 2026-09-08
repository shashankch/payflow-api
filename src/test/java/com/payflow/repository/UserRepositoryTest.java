package com.payflow.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import com.payflow.entity.User;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private UserRepository userRepository;

	private User savedUser;
	private UUID sampleReferenceId;

	@BeforeEach
	void setUp() {
		sampleReferenceId = UUID.randomUUID();
		User user = User.builder().referenceId(sampleReferenceId).name("Aarav Sharma").upiId("aarav@payflow")
				.phoneNumber("9988776655").balance(new BigDecimal("750.0000")).version(0L).createdAt(Instant.now())
				.updatedAt(Instant.now()).build();
		savedUser = entityManager.persistAndFlush(user);
	}

	@Test
	@DisplayName("Should find user by UPI ID")
	void shouldFindByUpiId() {
		Optional<User> found = userRepository.findByUpiId("aarav@payflow");

		assertThat(found).isPresent();
		assertThat(found.get().getName()).isEqualTo("Aarav Sharma");
		assertThat(found.get().getReferenceId()).isEqualTo(sampleReferenceId);
	}

	@Test
	@DisplayName("Should find user by reference UUID")
	void shouldFindByReferenceId() {
		Optional<User> found = userRepository.findByReferenceId(sampleReferenceId);

		assertThat(found).isPresent();
		assertThat(found.get().getUpiId()).isEqualTo("aarav@payflow");
	}

	@Test
	@DisplayName("Should acquire pessimistic lock when finding by UPI ID with lock")
	void shouldFindByUpiIdWithLock() {
		Optional<User> found = userRepository.findByUpiIdWithLock("aarav@payflow");

		assertThat(found).isPresent();
		assertThat(found.get().getUserId()).isEqualTo(savedUser.getUserId());
	}

	@Test
	@DisplayName("Should return true when checking exists by UPI ID")
	void shouldReturnTrue_whenExistsByUpiId() {
		boolean exists = userRepository.existsByUpiId("aarav@payflow");

		assertThat(exists).isTrue();
	}

	@Test
	@DisplayName("Should return false when checking exists by non-existent UPI ID")
	void shouldReturnFalse_whenNotExistsByUpiId() {
		boolean exists = userRepository.existsByUpiId("unknown@payflow");

		assertThat(exists).isFalse();
	}
}
