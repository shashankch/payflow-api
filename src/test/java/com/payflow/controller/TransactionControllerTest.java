package com.payflow.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import com.payflow.service.TransactionService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private TransactionService transactionService;

	@MockitoBean
	private TransactionMapper transactionMapper;

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
	@DisplayName("POST /api/v1/transactions — Should execute transfer and return 201 Created")
	void shouldExecuteTransfer_whenRequestIsValid() throws Exception {
		TransferMoneyRequest request = TransferMoneyRequest.builder().senderUpiId("alice@upi").receiverUpiId("bob@upi")
				.amount(new BigDecimal("100.00")).note("Dinner payment").build();

		UUID refId = UUID.randomUUID();
		Transaction createdTransaction = Transaction.builder().transactionId(10L).referenceId(refId)
				.senderUpiId("alice@upi").receiverUpiId("bob@upi").amount(new BigDecimal("100.00"))
				.status(TransactionStatus.COMPLETED).type(TransactionType.TRANSFER).note("Dinner payment")
				.createdAt(Instant.now()).build();

		given(transactionService.sendMoney(any(TransferMoneyRequest.class))).willReturn(createdTransaction);

		mockMvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request))).andExpect(status().isCreated())
				.andExpect(header().exists("Location")).andExpect(jsonPath("$.transactionId").value(10))
				.andExpect(jsonPath("$.senderUpiId").value("alice@upi"))
				.andExpect(jsonPath("$.receiverUpiId").value("bob@upi")).andExpect(jsonPath("$.amount").value(100.00))
				.andExpect(jsonPath("$.status").value("COMPLETED"));
	}

	@Test
	@DisplayName("POST /api/v1/transactions — Should return 422 Unprocessable Entity when amount is non-positive")
	void shouldReturn422_whenTransferAmountIsZeroOrNegative() throws Exception {
		TransferMoneyRequest invalidRequest = TransferMoneyRequest.builder().senderUpiId("alice@upi")
				.receiverUpiId("bob@upi").amount(new BigDecimal("0.00")).build();

		mockMvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(invalidRequest))).andExpect(status().isUnprocessableEntity());
	}

	@Test
	@DisplayName("GET /api/v1/transactions/{id} — Should return transaction details when found")
	void shouldReturnTransaction_whenFoundByReferenceId() throws Exception {
		UUID refId = UUID.randomUUID();
		Transaction tx = Transaction.builder().transactionId(10L).referenceId(refId).senderUpiId("alice@upi")
				.receiverUpiId("bob@upi").amount(new BigDecimal("100.00")).status(TransactionStatus.COMPLETED)
				.type(TransactionType.TRANSFER).createdAt(Instant.now()).build();

		given(transactionService.getTransactionByReferenceId(refId)).willReturn(tx);

		mockMvc.perform(get("/api/v1/transactions/" + refId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.referenceId").value(refId.toString()))
				.andExpect(jsonPath("$.senderUpiId").value("alice@upi")).andExpect(jsonPath("$.amount").value(100.00));
	}

	@Test
	@DisplayName("GET /api/v1/transactions/{id} — Should return 404 Not Found when transaction missing")
	void shouldReturn404_whenTransactionNotFound() throws Exception {
		UUID missingRefId = UUID.randomUUID();
		given(transactionService.getTransactionByReferenceId(missingRefId)).willThrow(
				new com.payflow.exception.TransactionNotFoundException("Transaction not found: " + missingRefId));

		mockMvc.perform(get("/api/v1/transactions/" + missingRefId)).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("GET /api/v1/transactions/user/{upiId} — Should return paginated transaction history")
	void shouldReturnPaginatedUserTransactions() throws Exception {
		Transaction tx = Transaction.builder().transactionId(1L).referenceId(UUID.randomUUID()).senderUpiId("alice@upi")
				.receiverUpiId("bob@upi").amount(new BigDecimal("50.00")).status(TransactionStatus.COMPLETED)
				.type(TransactionType.TRANSFER).createdAt(Instant.now()).build();

		org.springframework.data.domain.Page<Transaction> page = new org.springframework.data.domain.PageImpl<>(
				java.util.List.of(tx));

		given(transactionService.getUserTransactions(org.mockito.ArgumentMatchers.eq("alice@upi"), any()))
				.willReturn(page);

		mockMvc.perform(get("/api/v1/transactions/user/alice@upi")).andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].senderUpiId").value("alice@upi"))
				.andExpect(jsonPath("$.totalElements").value(1));
	}
}
