package com.park.ecommerce.product.dto;

import com.park.ecommerce.product.Product;
import com.park.ecommerce.product.status.StorageType;

public record ProductDetailResponse(
        Long productId,
        String productCode,
        String name,
        String brand,
        String description,
        Integer price,
        StorageType storageType,
        String thumbnailUrl,
        Long categoryId,
        boolean soldOut
) {
    public static ProductDetailResponse from(Product product) {
        return new ProductDetailResponse(
                product.getId(),
                product.getProductCode(),
                product.getName(),
                product.getBrand(),
                product.getDescription(),
                product.getPrice(),
                product.getStorageType(),
                product.getThumbnailUrl(),
                product.getCategoryId(),
                product.isSoldOut()
        );
    }
}
