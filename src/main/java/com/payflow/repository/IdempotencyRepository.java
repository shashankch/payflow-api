package com.payflow.repository;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.payflow.entity.IdempotencyRecord;

@Repository
public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, String> {

	@Modifying
	@Query("DELETE FROM IdempotencyRecord i WHERE i.createdAt < :cutoff")
	int deleteRecordsOlderThan(@Param("cutoff") Instant cutoff);
}
