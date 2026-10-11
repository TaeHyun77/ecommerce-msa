package com.park.ecommerce.search.suggestion;

// 자동완성 후보와 단계 점수 - 상품명·검색어 후보를 같은 점수 기준으로 합치기 위해 함께 돌려줌
public record ScoredSuggestion(String text, float score) {}
