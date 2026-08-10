package com.payflow.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import com.payflow.dto.response.LedgerEntryResponse;
import com.payflow.entity.BalanceLedgerEntry;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface LedgerMapper {

	@Mapping(source = "user.referenceId", target = "userReferenceId")
	@Mapping(source = "transaction.referenceId", target = "transactionReferenceId")
	LedgerEntryResponse toResponse(BalanceLedgerEntry entry);
}
