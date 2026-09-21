package com.park.ecommerce.product.dto;

import com.park.ecommerce.product.Product;
import com.park.ecommerce.product.status.StorageType;

// 상품 목록 한 건 - 고객에게는 정확한 재고 수량 대신 품절 여부만 노출
public record ProductListResponse(
        Long productId,
        String name,
        String brand,
        Integer price,
        StorageType storageType,
        String thumbnailUrl,
        Long categoryId,
        boolean soldOut
) {
    public static ProductListResponse from(Product product) {
        return new ProductListResponse(
                product.getId(),
                product.getName(),
                product.getBrand(),
                product.getPrice(),
                product.getStorageType(),
                product.getThumbnailUrl(),
                product.getCategoryId(),
                product.isSoldOut()
        );
    }
}
