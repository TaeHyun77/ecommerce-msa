package com.park.ecommerce.keyword;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchKeywordAggregationScheduler {
    private final SearchKeywordService searchKeywordService;

    // 이전 실행이 끝난 뒤 search.keyword.aggregate-delay만큼 기다림 - 기동 직후 한 번 바로 실행됨
    @Scheduled(fixedDelayString = "${search.keyword.aggregate-delay}")
    public void aggregate() {
        int indexed = searchKeywordService.aggregate();
        log.info("[검색어 집계] 자동완성 검색어 {}건 반영", indexed);
    }
}
