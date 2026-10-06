package com.park.ecommerce.product.dto;

import com.park.ecommerce.category.CategoryPath;
import com.park.ecommerce.product.Product;
import com.park.ecommerce.product.status.StorageType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProductBulkCreateRequest(
        @NotEmpty(message = "등록할 상품이 없습니다.")
        List<@Valid Item> products
) {
    // 파일로 받는 상품 정보라 카테고리를 식별자가 아닌 이름으로 받는다
    public record Item(
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

            @NotBlank(message = "상위 카테고리는 필수입니다.")
            String parentCategoryName,

            @NotBlank(message = "카테고리는 필수입니다.")
            String categoryName
    ) {
        public CategoryPath categoryPath() {
            return new CategoryPath(parentCategoryName, categoryName);
        }

        public Product toEntity(Long categoryId) {
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
}
