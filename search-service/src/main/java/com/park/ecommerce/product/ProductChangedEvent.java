package com.park.ecommerce.product;

// product-service가 발행하는 상품 스냅샷 - 공통 모듈로 공유하지 않고 필요한 필드만 복제
public record ProductChangedEvent(
        Long productId,
        String name,
        String brand,
        Long categoryId,
        Long parentCategoryId,
        String status, // 상품 서비스의 상태 enum에 묶이지 않도록 문자열로 받는다
        Integer price
) {}
