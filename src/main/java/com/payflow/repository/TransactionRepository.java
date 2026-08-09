package com.payflow.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.payflow.entity.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

	@EntityGraph(attributePaths = {"sender", "receiver"})
	Optional<Transaction> findByReferenceId(UUID referenceId);

	@EntityGraph(attributePaths = {"sender", "receiver"})
	Page<Transaction> findBySenderUpiIdOrReceiverUpiId(String senderUpiId, String receiverUpiId, Pageable pageable);
}
