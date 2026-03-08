package com.foodapp.orderservice.dto.response;

import org.springframework.data.domain.Page;
import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(List<T> content, long totalElements, int totalPages,
                               int currentPage, int pageSize) {
    public static <S, T> PageResponse<T> from(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getTotalElements(), page.getTotalPages(),
                page.getNumber(), page.getSize()
        );
    }
}
