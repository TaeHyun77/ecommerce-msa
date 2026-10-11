package com.park.ecommerce.keyword;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface SearchKeywordDocumentRepository extends ElasticsearchRepository<SearchKeywordDocument, String> {
    // 이번 집계 회차보다 먼저 쓴 문서 - 기준 미달이 됐거나 7일 집계 기간에서 빠진 검색어
    void deleteByAggregatedAtLessThan(Long aggregatedAt);
}
