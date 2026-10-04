package com.park.ecommerce.search.dto;

import java.util.List;

// 상품 목록 응답 (product-service ProductPageResponse)과 같은 형식
public record ProductSearchPageResponse(
        List<ProductSearchResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
