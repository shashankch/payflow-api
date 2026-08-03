package com.payflow.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.payflow.dto.request.TransferMoneyRequest;
import com.payflow.dto.response.TransactionResponse;
import com.payflow.entity.Transaction;
import com.payflow.mapper.TransactionMapper;
import com.payflow.service.TransactionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/transactions")
@Validated
@Tag(name = "Transactions", description = "Endpoints for initiating and tracking peer-to-peer money transfers")
public class TransactionController {

	private final TransactionService transactionService;
	private final TransactionMapper transactionMapper;

	public TransactionController(TransactionService transactionService, TransactionMapper transactionMapper) {
		this.transactionService = transactionService;
		this.transactionMapper = transactionMapper;
	}

	@PostMapping
	@Operation(summary = "Initiate peer-to-peer money transfer", description = "Transfers money between two "
			+ "valid UPI accounts")
	@ApiResponse(responseCode = "201", description = "Transaction successfully created and executed")
	@ApiResponse(responseCode = "400", description = "Invalid request payload or constraint validation error")
	public ResponseEntity<TransactionResponse> sendMoney(@Valid @RequestBody TransferMoneyRequest request) {
		Transaction createdTransaction = transactionService.sendMoney(request);
		TransactionResponse response = transactionMapper.toResponse(createdTransaction);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
				.buildAndExpand(createdTransaction.getTransactionId()).toUri();
		return ResponseEntity.created(location).body(response);
	}
}
