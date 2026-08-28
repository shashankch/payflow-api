package com.payflow.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;
import com.payflow.dto.request.TransferMoneyRequest;
import com.payflow.dto.response.TransactionResponse;
import com.payflow.entity.Transaction;
import com.payflow.entity.TransactionStatus;
import com.payflow.entity.TransactionType;
import com.payflow.mapper.TransactionMapper;
import com.payflow.repository.IdempotencyRepository;
import com.payflow.security.JwtAuthenticationEntryPoint;
import com.payflow.security.JwtAuthenticationFilter;
import com.payflow.security.JwtTokenProvider;
import com.payflow.service.TransactionService;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
@AutoConfigureMockMvc(addFilters = false)
class TransactionControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private TransactionService transactionService;

	@MockitoBean
	private TransactionMapper transactionMapper;

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
	void setUpMapperMock() {
		given(transactionMapper.toResponse(any())).willAnswer(invocation -> {
			Transaction tx = invocation.getArgument(0);
			if (tx == null) {
				return null;
			}
			return new TransactionResponse(tx.getTransactionId(), tx.getReferenceId(), tx.getSenderUpiId(),
					tx.getReceiverUpiId(), tx.getAmount(), tx.getStatus(), tx.getType(), tx.getNote(),
					tx.getCreatedAt());
		});
	}

	@Test
	@DisplayName("POST /api/v1/transactions with valid payload should return 201 Created and location header")
	void shouldReturnCreated_whenTransferRequestIsValid() throws Exception {
		UUID refId = UUID.randomUUID();
		TransferMoneyRequest request = TransferMoneyRequest.builder().senderUpiId("alice@payflow")
				.receiverUpiId("bob@payflow").amount(new BigDecimal("100.00")).note("Dinner split").build();

		Transaction tx = Transaction.builder().transactionId(1L).referenceId(refId).senderUpiId("alice@payflow")
				.receiverUpiId("bob@payflow").amount(new BigDecimal("100.00")).status(TransactionStatus.COMPLETED)
				.type(TransactionType.TRANSFER).note("Dinner split").createdAt(Instant.now()).build();

		given(transactionService.sendMoney(any(TransferMoneyRequest.class))).willReturn(tx);

		mockMvc.perform(post("/api/v1/transactions").header("Idempotency-Key", "test-key-123")
				.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", endsWith("/api/v1/transactions/" + refId)))
				.andExpect(jsonPath("$.referenceId").value(refId.toString()))
				.andExpect(jsonPath("$.amount").value(100.00)).andExpect(jsonPath("$.status").value("COMPLETED"));
	}

	@Test
	@DisplayName("POST /api/v1/transactions with invalid UPI should return 422 Unprocessable Entity")
	void shouldReturnUnprocessableEntity_whenUpiIsInvalid() throws Exception {
		TransferMoneyRequest request = TransferMoneyRequest.builder().senderUpiId("invalid_upi_no_at")
				.receiverUpiId("bob@payflow").amount(new BigDecimal("100.00")).build();

		mockMvc.perform(post("/api/v1/transactions").header("Idempotency-Key", "test-key-123")
				.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.title").value("Validation Failure"))
				.andExpect(jsonPath("$.errors.senderUpiId").exists());
	}

	@Test
	@DisplayName("POST /api/v1/transactions with negative amount should return 422 Unprocessable Entity")
	void shouldReturnUnprocessableEntity_whenAmountIsNegative() throws Exception {
		TransferMoneyRequest request = TransferMoneyRequest.builder().senderUpiId("alice@payflow")
				.receiverUpiId("bob@payflow").amount(new BigDecimal("-50.00")).build();

		mockMvc.perform(post("/api/v1/transactions").header("Idempotency-Key", "test-key-123")
				.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.title").value("Validation Failure"))
				.andExpect(jsonPath("$.errors.amount").exists());
	}

	@Test
	@DisplayName("POST /api/v1/transactions with missing note should be valid and return 201 Created")
	void shouldReturnCreated_whenOptionalNoteIsMissing() throws Exception {
		UUID refId = UUID.randomUUID();
		TransferMoneyRequest request = TransferMoneyRequest.builder().senderUpiId("alice@payflow")
				.receiverUpiId("bob@payflow").amount(new BigDecimal("50.00")).build();

		Transaction tx = Transaction.builder().transactionId(2L).referenceId(refId).senderUpiId("alice@payflow")
				.receiverUpiId("bob@payflow").amount(new BigDecimal("50.00")).status(TransactionStatus.COMPLETED)
				.type(TransactionType.TRANSFER).createdAt(Instant.now()).build();

		given(transactionService.sendMoney(any(TransferMoneyRequest.class))).willReturn(tx);

		mockMvc.perform(post("/api/v1/transactions").header("Idempotency-Key", "test-key-123")
				.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.referenceId").value(refId.toString()));
	}

	@Test
	@DisplayName("GET /api/v1/transactions/{id} should return 200 and transaction when found")
	void shouldReturnOk_whenTransactionFoundByReferenceId() throws Exception {
		UUID refId = UUID.randomUUID();
		Transaction tx = Transaction.builder().transactionId(3L).referenceId(refId).senderUpiId("alice@payflow")
				.receiverUpiId("bob@payflow").amount(new BigDecimal("75.00")).status(TransactionStatus.COMPLETED)
				.type(TransactionType.TRANSFER).createdAt(Instant.now()).build();

		given(transactionService.getTransactionByReferenceId(refId)).willReturn(tx);

		mockMvc.perform(get("/api/v1/transactions/" + refId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.referenceId").value(refId.toString()))
				.andExpect(jsonPath("$.amount").value(75.00));
	}
}
