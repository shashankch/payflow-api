package com.payflow.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.payflow.dto.request.LoginRequest;
import com.payflow.entity.User;
import com.payflow.exception.UserNotFoundException;
import com.payflow.repository.IdempotencyRepository;
import com.payflow.security.JwtAuthenticationEntryPoint;
import com.payflow.security.JwtAuthenticationFilter;
import com.payflow.security.JwtTokenProvider;
import com.payflow.service.UserService;

import tools.jackson.databind.ObjectMapper;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private UserService userService;

	@MockitoBean
	private IdempotencyRepository idempotencyRepository;

	@MockitoBean
	private JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	private JwtAuthenticationFilter jwtAuthenticationFilter;

	@MockitoBean
	private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

	@Test
	@DisplayName("POST /api/v1/auth/login should authenticate registered user and return JWT token")
	void shouldAuthenticateAndReturnToken_whenUserExists() throws Exception {
		UUID refId = UUID.randomUUID();
		String upiId = "alice@payflow";
		User user = User.builder().userId(1L).referenceId(refId).name("Alice").upiId(upiId)
				.balance(new BigDecimal("1000.00")).phoneNumber("9876543210").build();

		given(userService.getUserByUpiId(upiId)).willReturn(user);
		given(jwtTokenProvider.generateToken(upiId, refId)).willReturn("mocked-jwt-token-xyz");
		given(jwtTokenProvider.getExpirationSeconds()).willReturn(3600L);

		LoginRequest request = new LoginRequest(upiId);

		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request))).andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").value("mocked-jwt-token-xyz"))
				.andExpect(jsonPath("$.tokenType").value("Bearer")).andExpect(jsonPath("$.expiresIn").value(3600))
				.andExpect(jsonPath("$.upiId").value(upiId))
				.andExpect(jsonPath("$.referenceId").value(refId.toString()));
	}

	@Test
	@DisplayName("POST /api/v1/auth/login should return 404 Not Found when user does not exist")
	void shouldReturnNotFound_whenUserDoesNotExist() throws Exception {
		String upiId = "unknown@payflow";
		given(userService.getUserByUpiId(upiId)).willThrow(new UserNotFoundException("User not found: " + upiId));

		LoginRequest request = new LoginRequest(upiId);

		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request))).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("User Not Found"));
	}

	@Test
	@DisplayName("POST /api/v1/auth/login should return 422 Unprocessable Entity when UPI ID format is invalid")
	void shouldReturnUnprocessableEntity_whenUpiFormatIsInvalid() throws Exception {
		LoginRequest request = new LoginRequest("invalid-upi-without-at");

		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request))).andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.title").value("Validation Failure"));
	}
}
