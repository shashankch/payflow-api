package com.payflow.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;
import com.payflow.dto.request.CreateUserRequest;
import com.payflow.dto.response.UserResponse;
import com.payflow.entity.User;
import com.payflow.mapper.UserMapper;
import com.payflow.service.UserService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private UserService userService;

	@MockitoBean
	private UserMapper userMapper;

	@BeforeEach
	void setUpMapperMock() {
		given(userMapper.toResponse(any())).willAnswer(invocation -> {
			User user = invocation.getArgument(0);
			if (user == null) {
				return null;
			}
			return new UserResponse(user.getUserId(), user.getName(), user.getUpiId(), user.getBalance(),
					user.getPhoneNumber(), user.getCreatedAt(), user.getUpdatedAt());
		});
	}

	@Test
	@DisplayName("POST /api/v1/users — Should register user and return 201 Created")
	void shouldRegisterUser_whenRequestIsValid() throws Exception {
		CreateUserRequest request = CreateUserRequest.builder().name("Alice Smith").upiId("alice@upi")
				.phoneNumber("9876543210").balance(new BigDecimal("1000.00")).build();

		User createdUser = User.builder().userId(1L).name("Alice Smith").upiId("alice@upi").phoneNumber("9876543210")
				.balance(new BigDecimal("1000.00")).createdAt(Instant.now()).updatedAt(Instant.now()).build();

		given(userService.registerUser(any(CreateUserRequest.class))).willReturn(createdUser);

		mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request))).andExpect(status().isCreated())
				.andExpect(header().exists("Location")).andExpect(jsonPath("$.userId").value(1))
				.andExpect(jsonPath("$.name").value("Alice Smith")).andExpect(jsonPath("$.upiId").value("alice@upi"))
				.andExpect(jsonPath("$.balance").value(1000.00));
	}

	@Test
	@DisplayName("POST /api/v1/users — Should return 400 Bad Request when validation fails")
	void shouldReturn400_whenCreateUserValidationFails() throws Exception {
		CreateUserRequest invalidRequest = CreateUserRequest.builder().name("") // Blank name
				.upiId("invalid-upi-format").phoneNumber("123") // Not 10 digits
				.balance(new BigDecimal("-50.00")) // Negative balance
				.build();

		mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(invalidRequest))).andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("GET /api/v1/users/{id} — Should return user response when found")
	void shouldReturnUser_whenFoundById() throws Exception {
		User user = User.builder().userId(1L).name("Bob").upiId("bob@upi").phoneNumber("9876543211")
				.balance(new BigDecimal("500.00")).createdAt(Instant.now()).updatedAt(Instant.now()).build();

		given(userService.getUserById(1L)).willReturn(Optional.of(user));

		mockMvc.perform(get("/api/v1/users/1")).andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(1))
				.andExpect(jsonPath("$.upiId").value("bob@upi"));
	}

	@Test
	@DisplayName("GET /api/v1/users/{id} — Should return 404 Not Found when user does not exist")
	void shouldReturn404_whenUserNotFound() throws Exception {
		given(userService.getUserById(99L)).willReturn(Optional.empty());

		mockMvc.perform(get("/api/v1/users/99")).andExpect(status().isNotFound());
	}
}
