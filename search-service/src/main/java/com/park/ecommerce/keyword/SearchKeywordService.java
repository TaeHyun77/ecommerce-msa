package com.park.ecommerce.keyword;

import com.park.ecommerce.text.WordStarts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;

// 자동완성 후보로 쓸 검색어 횟수를 기록
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchKeywordService {
    private static final Duration DEDUPE_WINDOW = Duration.ofMinutes(30);

    private final SearchKeywordCountRepository searchKeywordCountRepository;

    // 실패해도 검색 응답에는 영향을 주지 않음 - 검색 로그는 일부 유실돼도 집계 결과가 크게 달라지지 않음
    // searcherId가 없으면 게이트웨이를 거치지 않았거나 쿠키를 돌려보내지 않은 요청이라 세지 않음
    public void record(String keyword, SearchSource source, String searcherId) {
        String keywordKey = WordStarts.compact(keyword);
        // 후보 클릭은 검색어 인덱스에 있는 검색어일 때만 센다 - 인덱스가 아직 없어 지금은 모두 제외
        if (searcherId == null || keywordKey.isEmpty() || source == SearchSource.SUGGESTION) {
            return;
        }
        try {
            if (searchKeywordCountRepository.markSeen(searcherId, keywordKey, DEDUPE_WINDOW)) {
                searchKeywordCountRepository.increment(LocalDate.now(), keyword);
            }
        } catch (DataAccessException e) {
            log.warn("[검색어 기록] 저장소 접근 실패로 건너뜀 keyword={}, 사유={}", keyword, e.getMessage());
        }
    }
}
