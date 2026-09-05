package com.payflow.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.payflow.dto.request.CreateUserRequest;
import com.payflow.dto.response.UserResponse;
import com.payflow.entity.User;

import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {

	private final UserMapper userMapper = Mappers.getMapper(UserMapper.class);

	@Test
	@DisplayName("Should correctly map User entity to UserResponse record")
	void shouldMapUserEntityToUserResponse() {
		Instant now = Instant.now();
		UUID refId = UUID.randomUUID();
		User user = User.builder().userId(100L).referenceId(refId).name("Alice Smith").upiId("alice@upi")
				.phoneNumber("9876543210").balance(new BigDecimal("1500.50")).version(1L).createdAt(now).updatedAt(now)
				.build();

		UserResponse response = userMapper.toResponse(user);

		assertThat(response).isNotNull();
		assertThat(response.referenceId()).isEqualTo(refId);
		assertThat(response.name()).isEqualTo("Alice Smith");
		assertThat(response.upiId()).isEqualTo("alice@upi");
		assertThat(response.phoneNumber()).isEqualTo("9876543210");
		assertThat(response.balance()).isEqualTo(new BigDecimal("1500.50"));
		assertThat(response.createdAt()).isEqualTo(now);
		assertThat(response.updatedAt()).isEqualTo(now);
	}

	@Test
	@DisplayName("Should correctly map CreateUserRequest to User entity")
	void shouldMapCreateUserRequestToUserEntity() {
		CreateUserRequest request = CreateUserRequest.builder().name("Bob Jones").upiId("bob@upi")
				.phoneNumber("9876543211").balance(new BigDecimal("250.00")).build();

		User user = userMapper.toEntity(request);

		assertThat(user).isNotNull();
		assertThat(user.getName()).isEqualTo("Bob Jones");
		assertThat(user.getUpiId()).isEqualTo("bob@upi");
		assertThat(user.getPhoneNumber()).isEqualTo("9876543211");
		assertThat(user.getBalance()).isEqualTo(new BigDecimal("250.00"));
	}
}
