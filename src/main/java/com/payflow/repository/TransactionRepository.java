package com.payflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.payflow.entity.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
}
