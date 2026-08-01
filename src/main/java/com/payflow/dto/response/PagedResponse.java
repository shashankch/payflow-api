package com.payflow.dto.response;

import java.util.List;

import org.springframework.data.domain.Page;

public record PagedResponse<T>(List<T> content, int pageNumber, int pageSize, long totalElements, int totalPages,
		boolean isLast) {
	public static <T> PagedResponse<T> fromPage(Page<T> page) {
		return new PagedResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
				page.getTotalPages(), page.isLast());
	}
}
