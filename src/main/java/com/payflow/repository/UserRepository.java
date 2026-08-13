package com.payflow.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.payflow.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

	// Derived JPA query parsed from method name
	Optional<User> findByUpiId(String upiId);

	boolean existsByUpiId(String upiId);

	// Pessimistic write lock query to prevent concurrent balance mutation race
	// conditions
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT u FROM User u WHERE u.upiId = :upiId")
	Optional<User> findByUpiIdWithLock(@Param("upiId") String upiId);

	Optional<User> findByReferenceId(UUID referenceId);

	// JPQL query to find users with balance greater than a specified amount
	@Query("SELECT u FROM User u WHERE u.balance > :amount")
	List<User> findUsersWithBalanceGreaterThan(@Param("amount") BigDecimal amount);
}
