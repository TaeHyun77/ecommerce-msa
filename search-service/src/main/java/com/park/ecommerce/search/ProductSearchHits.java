package com.park.ecommerce.search;

import java.util.List;

// ES가 찾은 상품 식별자(관련도순)와 조건에 맞는 전체 건수
public record ProductSearchHits(List<Long> productIds, long totalHits) {
}
