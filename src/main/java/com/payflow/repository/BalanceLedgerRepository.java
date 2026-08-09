package com.payflow.repository;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.payflow.entity.BalanceLedgerEntry;

public interface BalanceLedgerRepository extends JpaRepository<BalanceLedgerEntry, Long> {

	@EntityGraph(attributePaths = {"user", "transaction"})
	Page<BalanceLedgerEntry> findByUserReferenceIdOrderByCreatedAtDesc(UUID referenceId, Pageable pageable);

	@Query("SELECT COALESCE(SUM(CASE WHEN e.entryType = 'CREDIT' THEN e.amount ELSE -e.amount END), 0) "
			+ "FROM BalanceLedgerEntry e WHERE e.user.userId = :userId")
	BigDecimal calculateReconciledBalanceByUserId(@Param("userId") Long userId);
}
