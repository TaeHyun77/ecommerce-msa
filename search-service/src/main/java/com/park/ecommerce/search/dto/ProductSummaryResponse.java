package com.park.ecommerce.search.dto;

// product-service 내부 API(/internal/products)의 응답 중 검색 결과에 필요한 필드만 직접 정의한 DTO
// 상태·보관 방법은 product-service의 enum에 묶이지 않도록 문자열로 받는다
public record ProductSummaryResponse(
        Long productId,
        String name,
        String brand,
        Integer price,
        String storageType,
        String thumbnailUrl,
        Long categoryId,
        String status,
        Integer availableQuantity
) {
    private static final String ON_SALE = "ON_SALE";

    public boolean isOnSale() {
        return ON_SALE.equals(status);
    }
}
