package com.payflow.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;
import com.payflow.dto.request.TransferMoneyRequest;
import com.payflow.entity.Transaction;
import com.payflow.entity.TransactionStatus;
import com.payflow.entity.TransactionType;
import com.payflow.service.TransactionService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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

	@Test
	@DisplayName("POST /api/v1/transactions — Should execute transfer and return 201 Created")
	void shouldSendMoney_whenRequestIsValid() throws Exception {
		TransferMoneyRequest request = TransferMoneyRequest.builder().senderUpiId("alice@upi").receiverUpiId("bob@upi")
				.amount(new BigDecimal("150.00")).note("Dinner split").build();

		Transaction transaction = Transaction.builder().transactionId(101L).referenceId(UUID.randomUUID())
				.senderUpiId("alice@upi").receiverUpiId("bob@upi").amount(new BigDecimal("150.00"))
				.status(TransactionStatus.COMPLETED).type(TransactionType.TRANSFER).note("Dinner split")
				.createdAt(Instant.now()).build();

		given(transactionService.sendMoney(any(TransferMoneyRequest.class))).willReturn(transaction);

		mockMvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request))).andExpect(status().isCreated())
				.andExpect(header().exists("Location")).andExpect(jsonPath("$.transactionId").value(101))
				.andExpect(jsonPath("$.senderUpiId").value("alice@upi"))
				.andExpect(jsonPath("$.receiverUpiId").value("bob@upi")).andExpect(jsonPath("$.amount").value(150.00))
				.andExpect(jsonPath("$.status").value("COMPLETED"));
	}

	@Test
	@DisplayName("POST /api/v1/transactions — Should return 400 Bad Request when validation fails")
	void shouldReturn400_whenTransferValidationFails() throws Exception {
		TransferMoneyRequest invalidRequest = TransferMoneyRequest.builder().senderUpiId("") // Blank sender
				.receiverUpiId("invalid-upi") // Invalid format
				.amount(new BigDecimal("0.00")) // Amount < 0.01
				.build();

		mockMvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(invalidRequest))).andExpect(status().isBadRequest());
	}
}
