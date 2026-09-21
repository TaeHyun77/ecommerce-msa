package com.park.ecommerce.product.dto;

import com.park.ecommerce.product.Product;
import com.park.ecommerce.product.status.StorageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ProductCreateRequest(
        @NotBlank(message = "상품코드는 필수입니다.")
        String productCode,

        @NotBlank(message = "상품명은 필수입니다.")
        String name,

        String brand,

        @Size(max = 500, message = "상품 설명은 500자 이하여야 합니다.")
        String description,

        @NotNull(message = "보관 방법은 필수입니다.")
        StorageType storageType,

        @NotNull(message = "판매가는 필수입니다.")
        @PositiveOrZero(message = "판매가는 0원 이상이어야 합니다.")
        Integer price,

        String thumbnailUrl,

        @NotNull(message = "카테고리는 필수입니다.")
        Long categoryId
) {
    public Product toEntity() {
        return Product.builder()
                .productCode(productCode)
                .name(name)
                .brand(brand)
                .description(description)
                .storageType(storageType)
                .price(price)
                .thumbnailUrl(thumbnailUrl)
                .categoryId(categoryId)
                .build();
    }
}
