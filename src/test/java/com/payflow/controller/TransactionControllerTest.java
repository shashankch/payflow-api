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
import com.payflow.dto.response.SpendInsightResponse;
import com.payflow.dto.response.TransactionResponse;
import com.payflow.entity.Transaction;
import com.payflow.entity.TransactionStatus;
import com.payflow.entity.TransactionType;
import com.payflow.exception.FeatureDisabledException;
import com.payflow.exception.ForbiddenOperationException;
import com.payflow.exception.TransactionNotFoundException;
import com.payflow.mapper.TransactionMapper;
import com.payflow.repository.IdempotencyRepository;
import com.payflow.security.JwtAccessDeniedHandler;
import com.payflow.security.JwtAuthenticationEntryPoint;
import com.payflow.security.JwtAuthenticationFilter;
import com.payflow.security.JwtTokenProvider;
import com.payflow.service.SpendInsightsService;
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
	private SpendInsightsService spendInsightsService;

	@MockitoBean
	private IdempotencyRepository idempotencyRepository;

	@MockitoBean
	private JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	private JwtAuthenticationFilter jwtAuthenticationFilter;

	@MockitoBean
	private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

	@MockitoBean
	private JwtAccessDeniedHandler jwtAccessDeniedHandler;

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

	@Test
	@DisplayName("POST /api/v1/transactions/{id}/insights should return 200 and insights when successful")
	void shouldReturn200_whenSpendInsightsGeneratedSuccessfully() throws Exception {
		UUID refId = UUID.randomUUID();
		SpendInsightResponse response = new SpendInsightResponse( //
				refId, //
				"FOOD_AND_DINING", //
				new BigDecimal("150.00"), //
				"Dining expenditure at cafe.", //
				"Set a weekly dining out limit.", //
				0.95, //
				"AI_MODEL");

		given(spendInsightsService.generateSpendInsights(refId)).willReturn(response);

		mockMvc.perform(post("/api/v1/transactions/" + refId + "/insights")) //
				.andExpect(status().isOk()) //
				.andExpect(jsonPath("$.category").value("FOOD_AND_DINING")) //
				.andExpect(jsonPath("$.source").value("AI_MODEL")) //
				.andExpect(jsonPath("$.confidenceScore").value(0.95));
	}

	@Test
	@DisplayName("POST /api/v1/transactions/{id}/insights should return 404 when transaction not found")
	void shouldReturn404_whenTransactionNotFoundForInsights() throws Exception {
		UUID refId = UUID.randomUUID();
		given(spendInsightsService.generateSpendInsights(refId)) //
				.willThrow(new TransactionNotFoundException("Transaction not found: " + refId));

		mockMvc.perform(post("/api/v1/transactions/" + refId + "/insights")) //
				.andExpect(status().isNotFound()) //
				.andExpect(jsonPath("$.title").value("Transaction Not Found"));
	}

	@Test
	@DisplayName("POST /api/v1/transactions/{id}/insights should return 403 when forbidden")
	void shouldReturn403_whenForbiddenForInsights() throws Exception {
		UUID refId = UUID.randomUUID();
		given(spendInsightsService.generateSpendInsights(refId)) //
				.willThrow(new ForbiddenOperationException("Not authorized"));

		mockMvc.perform(post("/api/v1/transactions/" + refId + "/insights")) //
				.andExpect(status().isForbidden()) //
				.andExpect(jsonPath("$.title").value("Forbidden Operation"));
	}

	@Test
	@DisplayName("POST /api/v1/transactions/{id}/insights should return 503 when AI feature is disabled")
	void shouldReturn503_whenAiFeatureDisabledForInsights() throws Exception {
		UUID refId = UUID.randomUUID();
		given(spendInsightsService.generateSpendInsights(refId)) //
				.willThrow(new FeatureDisabledException("Feature disabled"));

		mockMvc.perform(post("/api/v1/transactions/" + refId + "/insights")) //
				.andExpect(status().isServiceUnavailable()) //
				.andExpect(jsonPath("$.title").value("Feature Disabled"));
	}

	@Test
	@DisplayName("GET /api/v1/transactions/user/{upiId} should return 200 and paginated transactions")
	void shouldReturnUserTransactions() throws Exception {
		Transaction tx = Transaction.builder().transactionId(4L).referenceId(UUID.randomUUID())
				.senderUpiId("alice@payflow").receiverUpiId("bob@payflow").amount(new BigDecimal("20.00"))
				.status(TransactionStatus.COMPLETED).type(TransactionType.TRANSFER).createdAt(Instant.now()).build();

		given(transactionService.getUserTransactions(any(), any()))
				.willReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(tx)));

		mockMvc.perform(get("/api/v1/transactions/user/alice@payflow")).andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].senderUpiId").value("alice@payflow"))
				.andExpect(jsonPath("$.content[0].amount").value(20.00));
	}
}
