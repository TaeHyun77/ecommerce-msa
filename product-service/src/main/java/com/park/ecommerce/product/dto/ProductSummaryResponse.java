package com.park.ecommerce.product.dto;

import com.park.ecommerce.product.Product;
import com.park.ecommerce.product.status.ProductStatus;
import com.park.ecommerce.product.status.StorageType;

// 장바구니·검색 등 다른 서비스가 상품 목록을 화면에 그릴 때 필요한 정보 - 검색 결과가 상품 목록과 같은 형식이 되도록 목록 응답의 필드를 모두 담음
// availableQuantity는 담을 수량을 검증하기 위한 값이라 내부 통신에서만 사용한다
public record ProductSummaryResponse(
        Long productId,
        String name,
        String brand,
        Integer price,
        StorageType storageType,
        String thumbnailUrl,
        Long categoryId,
        ProductStatus status,
        Integer availableQuantity
) {
    public static ProductSummaryResponse from(Product product) {
        return new ProductSummaryResponse(
                product.getId(),
                product.getName(),
                product.getBrand(),
                product.getPrice(),
                product.getStorageType(),
                product.getThumbnailUrl(),
                product.getCategoryId(),
                product.getStatus(),
                product.getStockQuantity()
        );
    }
}
