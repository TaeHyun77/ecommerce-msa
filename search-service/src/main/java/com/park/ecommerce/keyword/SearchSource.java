package com.park.ecommerce.keyword;

// 검색이 어떻게 시작됐는지 - 자동완성 후보를 눌러 시작한 검색은 상품명 후보가 검색어로 섞이지 않도록 따로 판정
public enum SearchSource {
    TYPED,
    SUGGESTION
}
