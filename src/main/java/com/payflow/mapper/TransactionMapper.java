package com.payflow.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import com.payflow.dto.response.TransactionResponse;
import com.payflow.entity.Transaction;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TransactionMapper {

	TransactionResponse toResponse(Transaction transaction);
}
