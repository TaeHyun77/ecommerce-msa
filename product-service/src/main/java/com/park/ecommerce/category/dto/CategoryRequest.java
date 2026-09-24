package com.park.ecommerce.category.dto;

import com.park.ecommerce.category.Category;
import jakarta.validation.constraints.NotBlank;

public record CategoryRequest(
        @NotBlank(message = "카테고리명은 필수입니다.")
        String name,

        Long parentId // 없으면 상위 카테고리로 등록
) {
    public Category toEntity() {
        return Category.builder()
                .name(name)
                .parentId(parentId)
                .build();
    }
}
