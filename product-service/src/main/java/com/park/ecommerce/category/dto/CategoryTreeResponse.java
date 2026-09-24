package com.park.ecommerce.category.dto;

import com.park.ecommerce.category.Category;

import java.util.List;

public record CategoryTreeResponse(
        Long categoryId,
        String name,
        List<CategoryTreeResponse> children // 하위 카테고리는 빈 목록
) {
    public static CategoryTreeResponse of(Category category, List<Category> children) {
        return new CategoryTreeResponse(
                category.getId(),
                category.getName(),
                children.stream().map(child -> of(child, List.of())).toList()
        );
    }
}
