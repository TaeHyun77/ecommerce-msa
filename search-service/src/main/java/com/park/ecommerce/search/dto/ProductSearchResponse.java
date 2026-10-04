package com.park.ecommerce.search.dto;

// 검색 결과 한 건 - 프론트엔드가 같은 상품 카드를 쓰도록 상품 목록 응답(product-service ProductListResponse)과 같은 형식
public record ProductSearchResponse(
        Long productId,
        String name,
        String brand,
        Integer price,
        String storageType,
        String thumbnailUrl,
        Long categoryId,
        boolean soldOut
) {
    // 고객에게는 정확한 재고 수량 대신 품절 여부만 노출
    public static ProductSearchResponse from(ProductSummaryResponse product) {
        return new ProductSearchResponse(
                product.productId(),
                product.name(),
                product.brand(),
                product.price(),
                product.storageType(),
                product.thumbnailUrl(),
                product.categoryId(),
                product.availableQuantity() == 0
        );
    }
}
