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

	@MockitoBean
	private LedgerMapper ledgerMapper;

	@MockitoBean
	private IdempotencyRepository idempotencyRepository;

	@BeforeEach
	void setUpMapperMock() {
		given(userMapper.toResponse(any())).willAnswer(invocation -> {
			User user = invocation.getArgument(0);
			if (user == null) {
				return null;
			}
			return new UserResponse(user.getReferenceId(), user.getName(), user.getUpiId(), user.getBalance(),
					user.getPhoneNumber(), user.getCreatedAt(), user.getUpdatedAt());
		});

		given(ledgerMapper.toResponse(any())).willAnswer(invocation -> {
			BalanceLedgerEntry entry = invocation.getArgument(0);
			if (entry == null) {
				return null;
			}
			return new LedgerEntryResponse(entry.getLedgerId(),
					entry.getUser() != null ? entry.getUser().getReferenceId() : null,
					entry.getTransaction() != null ? entry.getTransaction().getReferenceId() : null,
					entry.getEntryType(), entry.getAmount(), entry.getBalanceBefore(), entry.getBalanceAfter(),
					entry.getCreatedAt());
		});
	}

	@Test
	@DisplayName("POST /api/v1/users — Should register user and return 201 Created")
	void shouldRegisterUser_whenRequestIsValid() throws Exception {
		UUID refId = UUID.randomUUID();
		CreateUserRequest request = CreateUserRequest.builder().name("Alice Smith").upiId("alice@upi")
				.phoneNumber("9876543210").balance(new BigDecimal("1000.00")).build();

		User createdUser = User.builder().userId(1L).referenceId(refId).name("Alice Smith").upiId("alice@upi")
				.phoneNumber("9876543210").balance(new BigDecimal("1000.00")).createdAt(Instant.now())
				.updatedAt(Instant.now()).build();

		given(userService.registerUser(any(CreateUserRequest.class))).willReturn(createdUser);

		mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request))).andExpect(status().isCreated())
				.andExpect(header().exists("Location")).andExpect(jsonPath("$.referenceId").value(refId.toString()))
				.andExpect(jsonPath("$.name").value("Alice Smith")).andExpect(jsonPath("$.upiId").value("alice@upi"))
				.andExpect(jsonPath("$.balance").value(1000.00));
	}

	@Test
	@DisplayName("POST /api/v1/users — Should return 422 Unprocessable Entity when validation fails")
	void shouldReturn422_whenCreateUserValidationFails() throws Exception {
		CreateUserRequest invalidRequest = CreateUserRequest.builder().name("") // Blank name
				.upiId("invalid-upi-format").phoneNumber("123") // Not 10 digits
				.balance(new BigDecimal("-50.00")) // Negative balance
				.build();

		mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(invalidRequest))).andExpect(status().isUnprocessableEntity());
	}

	@Test
	@DisplayName("GET /api/v1/users/{id} — Should return user response when found")
	void shouldReturnUser_whenFoundById() throws Exception {
		UUID refId = UUID.randomUUID();
		User user = User.builder().userId(1L).referenceId(refId).name("Bob").upiId("bob@upi").phoneNumber("9876543211")
				.balance(new BigDecimal("500.00")).createdAt(Instant.now()).updatedAt(Instant.now()).build();

		given(userService.getUserByReferenceId(refId)).willReturn(Optional.of(user));

		mockMvc.perform(get("/api/v1/users/" + refId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.referenceId").value(refId.toString()))
				.andExpect(jsonPath("$.upiId").value("bob@upi"));
	}

	@Test
	@DisplayName("GET /api/v1/users/{id} — Should return 404 Not Found when user does not exist")
	void shouldReturn404_whenUserNotFound() throws Exception {
		UUID missingRefId = UUID.randomUUID();
		given(userService.getUserByReferenceId(missingRefId)).willReturn(Optional.empty());

		mockMvc.perform(get("/api/v1/users/" + missingRefId)).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("GET /api/v1/users/{id}/ledger — Should return paginated ledger entries when user exists")
	void shouldReturnUserLedger_whenUserExists() throws Exception {
		UUID refId = UUID.randomUUID();
		BalanceLedgerEntry entry = BalanceLedgerEntry.builder().ledgerId(1L).entryType(LedgerEntryType.DEBIT)
				.amount(new BigDecimal("100.00")).balanceBefore(new BigDecimal("500.00"))
				.balanceAfter(new BigDecimal("400.00")).createdAt(Instant.now()).build();
		Page<BalanceLedgerEntry> page = new PageImpl<>(List.of(entry));

		given(userService.getUserLedger(any(UUID.class), any(Pageable.class))).willReturn(page);

		mockMvc.perform(get("/api/v1/users/" + refId + "/ledger")).andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].ledgerId").value(1))
				.andExpect(jsonPath("$.content[0].entryType").value("DEBIT"))
				.andExpect(jsonPath("$.content[0].amount").value(100.00));
	}
}
