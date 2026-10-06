package com.park.ecommerce.product;

import com.park.ecommerce.product.status.ProductStatus;

// 검색 색인용 상품 스냅샷 - 소비자가 상품 서비스를 다시 조회하지 않도록 색인에 필요한 값을 모두 담는다
public record ProductChangedEvent(
        Long productId,
        String name,
        String brand,
        Long categoryId,
        Long parentCategoryId,
        ProductStatus status,
        Integer price
) {
    public static ProductChangedEvent from(Product product, Long parentCategoryId) {
        return new ProductChangedEvent(
                product.getId(),
                product.getName(),
                product.getBrand(),
                product.getCategoryId(),
                parentCategoryId,
                product.getStatus(),
                product.getPrice()
        );
    }
}
