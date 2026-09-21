package com.park.ecommerce.product.dto;

import com.park.ecommerce.product.Product;
import com.park.ecommerce.product.status.ProductStatus;

// 장바구니 등 다른 서비스가 상품 목록을 화면에 그릴 때 필요한 최소 정보
// availableQuantity는 담을 수량을 검증하기 위한 값이라 내부 통신에서만 사용한다
public record ProductSummaryResponse(
        Long productId,
        String name,
        Integer price,
        String thumbnailUrl,
        ProductStatus status,
        Integer availableQuantity
) {
    public static ProductSummaryResponse from(Product product) {
        return new ProductSummaryResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getThumbnailUrl(),
                product.getStatus(),
                product.getStockQuantity()
        );
    }
}
