package com.park.ecommerce.product.dto;

import com.park.ecommerce.product.Product;
import com.park.ecommerce.product.status.ProductStatus;
import com.park.ecommerce.product.status.StorageType;

public record ProductResponse(
        Long productId,
        String productCode,
        String name,
        String brand,
        String description,
        ProductStatus status,
        StorageType storageType,
        Integer price,
        String thumbnailUrl,
        Long categoryId
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getProductCode(),
                product.getName(),
                product.getBrand(),
                product.getDescription(),
                product.getStatus(),
                product.getStorageType(),
                product.getPrice(),
                product.getThumbnailUrl(),
                product.getCategoryId()
        );
    }
}
