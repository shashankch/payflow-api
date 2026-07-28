package com.payflow.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.payflow.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

	// Derived JPA query parsed from method name
	Optional<User> findByUpiId(String upiId);

	// JPQL query to find users with balance greater than a specified amount
	@Query("SELECT u FROM User u WHERE u.balance > :amount")
	List<User> findUsersWithBalanceGreaterThan(@Param("amount") Double amount);
}
