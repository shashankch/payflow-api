package com.payflow.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.payflow.dto.request.TransferMoneyRequest;
import com.payflow.dto.response.PagedResponse;
import com.payflow.dto.response.TransactionResponse;
import com.payflow.entity.Transaction;
import com.payflow.exception.TransactionNotFoundException;
import com.payflow.mapper.TransactionMapper;
import com.payflow.service.TransactionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

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
				.buildAndExpand(createdTransaction.getReferenceId()).toUri();
		return ResponseEntity.created(location).body(response);
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get transaction by reference ID", description = "Fetches transaction by reference ID")
	@ApiResponse(responseCode = "200", description = "Transaction found and returned")
	@ApiResponse(responseCode = "404", description = "Transaction not found")
	public ResponseEntity<TransactionResponse> getTransactionByReferenceId(@PathVariable UUID id) {
		Transaction tx = transactionService.getTransactionByReferenceId(id)
				.orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + id));
		return ResponseEntity.ok(transactionMapper.toResponse(tx));
	}

	@GetMapping("/user/{upiId}")
	@Operation(summary = "Get user transactions", description = "Retrieves transaction history for a UPI ID")
	@ApiResponse(responseCode = "200", description = "Paginated transaction list returned")
	public ResponseEntity<PagedResponse<TransactionResponse>> getUserTransactions(@PathVariable String upiId,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
			@RequestParam(defaultValue = "createdAt") String sortBy) {
		Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy).descending());
		Page<TransactionResponse> txPage = transactionService.getUserTransactions(upiId, pageable)
				.map(transactionMapper::toResponse);
		return ResponseEntity.ok(PagedResponse.fromPage(txPage));
	}
}
