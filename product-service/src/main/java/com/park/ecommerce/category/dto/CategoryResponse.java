package com.park.ecommerce.category.dto;

import com.park.ecommerce.category.Category;

public record CategoryResponse(
        Long categoryId,
        String name,
        Long parentId
) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getParentId()
        );
    }
}
