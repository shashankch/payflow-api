package com.payflow.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import com.payflow.dto.request.CreateUserRequest;
import com.payflow.dto.response.UserResponse;
import com.payflow.entity.User;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {

	UserResponse toResponse(User user);

	@Mapping(target = "userId", ignore = true)
	@Mapping(target = "version", ignore = true)
	@Mapping(target = "createdAt", ignore = true)
	@Mapping(target = "updatedAt", ignore = true)
	User toEntity(CreateUserRequest request);
}
