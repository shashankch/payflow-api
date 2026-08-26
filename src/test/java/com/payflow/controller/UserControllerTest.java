package com.payflow.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;
import com.payflow.dto.request.CreateUserRequest;
import com.payflow.dto.response.LedgerEntryResponse;
import com.payflow.dto.response.UserResponse;
import com.payflow.entity.BalanceLedgerEntry;
import com.payflow.entity.LedgerEntryType;
import com.payflow.entity.User;
import com.payflow.mapper.LedgerMapper;
import com.payflow.mapper.UserMapper;
import com.payflow.repository.IdempotencyRepository;
import com.payflow.security.JwtAuthenticationEntryPoint;
import com.payflow.security.JwtAuthenticationFilter;
import com.payflow.security.JwtTokenProvider;
import com.payflow.service.UserService;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private UserService userService;

	@MockitoBean
	private UserMapper userMapper;

	@MockitoBean
	private LedgerMapper ledgerMapper;

	@MockitoBean
	private IdempotencyRepository idempotencyRepository;

	@MockitoBean
	private JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	private JwtAuthenticationFilter jwtAuthenticationFilter;

	@MockitoBean
	private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

	@MockitoBean
	private com.payflow.security.JwtAccessDeniedHandler jwtAccessDeniedHandler;

	@BeforeEach
	void setUpMappers() {
		given(userMapper.toResponse(any())).willAnswer(invocation -> {
			User u = invocation.getArgument(0);
			if (u == null) {
				return null;
			}
			return new UserResponse(u.getReferenceId(), u.getName(), u.getUpiId(), u.getBalance(), u.getPhoneNumber(),
					u.getCreatedAt(), u.getUpdatedAt());
		});

		given(ledgerMapper.toResponse(any())).willAnswer(invocation -> {
			BalanceLedgerEntry e = invocation.getArgument(0);
			if (e == null) {
				return null;
			}
			return new LedgerEntryResponse(e.getLedgerId(), e.getUser() != null ? e.getUser().getReferenceId() : null,
					e.getTransaction() != null ? e.getTransaction().getReferenceId() : null, e.getEntryType(),
					e.getAmount(), e.getBalanceBefore(), e.getBalanceAfter(), e.getCreatedAt());
		});
	}

	@Test
	@DisplayName("POST /api/v1/users should create user and return 201 Created")
	void shouldCreateUserAndReturnCreated() throws Exception {
		UUID refId = UUID.randomUUID();
		CreateUserRequest request = new CreateUserRequest();
		request.setName("Alice");
		request.setUpiId("alice@payflow");
		request.setPhoneNumber("9876543210");
		request.setBalance(new BigDecimal("500.00"));

		User savedUser = User.builder().userId(1L).referenceId(refId).name("Alice").upiId("alice@payflow")
				.balance(new BigDecimal("500.00")).phoneNumber("9876543210").version(0L).createdAt(Instant.now())
				.updatedAt(Instant.now()).build();

		given(userService.registerUser(any(CreateUserRequest.class))).willReturn(savedUser);

		mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request))).andExpect(status().isCreated())
				.andExpect(header().string("Location", endsWith("/api/v1/users/" + refId)))
				.andExpect(jsonPath("$.referenceId").value(refId.toString()))
				.andExpect(jsonPath("$.name").value("Alice")).andExpect(jsonPath("$.upiId").value("alice@payflow"))
				.andExpect(jsonPath("$.balance").value(500.00));
	}

	@Test
	@DisplayName("POST /api/v1/users with invalid payload should return 422 Unprocessable Entity")
	void shouldReturnUnprocessableEntity_whenPayloadIsInvalid() throws Exception {
		CreateUserRequest request = new CreateUserRequest();
		request.setName(""); // Invalid @NotBlank
		request.setUpiId("invalid_upi"); // Invalid @Pattern
		request.setPhoneNumber("123"); // Invalid @Pattern

		mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request))).andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.title").value("Validation Failure"))
				.andExpect(jsonPath("$.errors.name").exists()).andExpect(jsonPath("$.errors.upiId").exists())
				.andExpect(jsonPath("$.errors.phoneNumber").exists());
	}

	@Test
	@DisplayName("GET /api/v1/users/{id} should return 200 OK and user details when found")
	void shouldReturnUser_whenFoundByReferenceId() throws Exception {
		UUID refId = UUID.randomUUID();
		User user = User.builder().userId(2L).referenceId(refId).name("Bob").upiId("bob@payflow")
				.balance(new BigDecimal("300.00")).phoneNumber("9876543211").version(0L).createdAt(Instant.now())
				.updatedAt(Instant.now()).build();

		given(userService.getUserByReferenceId(refId)).willReturn(Optional.of(user));

		mockMvc.perform(get("/api/v1/users/" + refId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.referenceId").value(refId.toString())).andExpect(jsonPath("$.name").value("Bob"))
				.andExpect(jsonPath("$.balance").value(300.00));
	}

	@Test
	@DisplayName("GET /api/v1/users/{id} should return 404 Not Found when user does not exist")
	void shouldReturnNotFound_whenUserNotFoundByReferenceId() throws Exception {
		UUID refId = UUID.randomUUID();
		given(userService.getUserByReferenceId(refId)).willReturn(Optional.empty());

		mockMvc.perform(get("/api/v1/users/" + refId)).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("User Not Found"));
	}

	@Test
	@DisplayName("GET /api/v1/users/{id}/ledger should return 200 OK and paginated ledger entries")
	void shouldReturnUserLedgerEntries() throws Exception {
		UUID refId = UUID.randomUUID();
		User user = User.builder().userId(1L).referenceId(refId).build();
		BalanceLedgerEntry entry = BalanceLedgerEntry.builder().ledgerId(10L).user(user)
				.entryType(LedgerEntryType.DEBIT).amount(new BigDecimal("100.00"))
				.balanceBefore(new BigDecimal("500.00")).balanceAfter(new BigDecimal("400.00")).createdAt(Instant.now())
				.build();

		Page<BalanceLedgerEntry> page = new PageImpl<>(List.of(entry));
		given(userService.getUserLedger(any(UUID.class), any(Pageable.class))).willReturn(page);

		mockMvc.perform(get("/api/v1/users/" + refId + "/ledger")).andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].ledgerId").value(10))
				.andExpect(jsonPath("$.content[0].entryType").value("DEBIT"))
				.andExpect(jsonPath("$.content[0].amount").value(100.00));
	}
}
