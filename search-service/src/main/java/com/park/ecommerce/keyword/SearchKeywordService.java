package com.park.ecommerce.keyword;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 자동완성 후보로 쓸 검색어 횟수를 기록하고, 주기마다 집계해 검색어 인덱스를 다시 만듦
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchKeywordService {
    private static final Duration DEDUPE_WINDOW = Duration.ofMinutes(30);
    private static final int WINDOW_DAYS = 7;
    private static final double DAILY_DECAY = 0.9;
    private static final double MIN_POPULARITY = 3; // 이보다 적게 검색된 검색어는 오타·장난일 가능성이 커서 후보로 쓰지 않음

    private final SearchKeywordCountRepository searchKeywordCountRepository;
    private final SearchKeywordDocumentRepository searchKeywordDocumentRepository;

    // 실패해도 검색 응답에는 영향을 주지 않음 - 검색 로그는 일부 유실돼도 집계 결과가 크게 달라지지 않음
    // searcherId가 없으면 게이트웨이를 거치지 않았거나 쿠키를 돌려보내지 않은 요청이라 세지 않음
    public void record(String keyword, SearchSource source, String searcherId) {
        String keywordKey = SearchKeywordDocument.keyOf(keyword);
        if (searcherId == null || keywordKey.isEmpty()) {
            return;
        }
        try {
            // 후보 클릭은 검색어 인덱스에 이미 있는 검색어만 셈 - 상품명 후보 클릭이 검색어로 섞이지 않게 함
            if (source == SearchSource.SUGGESTION && !searchKeywordDocumentRepository.existsById(keywordKey)) {
                return;
            }
            if (searchKeywordCountRepository.markSeen(searcherId, keywordKey, DEDUPE_WINDOW)) {
                searchKeywordCountRepository.increment(LocalDate.now(), keyword);
            }
        } catch (DataAccessException e) {
            log.warn("[검색어 기록] 저장소 접근 실패로 건너뜀 keyword={}, 사유={}", keyword, e.getMessage());
        }
    }

    // 최근 7일 검색 횟수로 검색어 인덱스를 다시 만들고 반영한 문서 수를 돌려줌
    // Redis를 읽지 못하면 예외가 그대로 나가 삭제까지 가지 않으므로 기존 인덱스가 유지됨
    public int aggregate() {
        long aggregatedAt = System.currentTimeMillis();
        Map<String, Double> counts = searchKeywordCountRepository.sumDecayed(LocalDate.now(), WINDOW_DAYS, DAILY_DECAY);

        List<SearchKeywordDocument> documents = toDocuments(counts, aggregatedAt);
        if (!documents.isEmpty()) {
            searchKeywordDocumentRepository.saveAll(documents);
        }
        // 저장소의 저장은 끝나면 인덱스를 refresh하므로, 방금 쓴 문서는 이 삭제 조건에 걸리지 않음
        searchKeywordDocumentRepository.deleteByAggregatedAtLessThan(aggregatedAt);
        return documents.size();
    }

    // 띄어쓰기·기호만 다른 표기를 한 검색어로 묶고, 가장 많이 검색된 표기를 보여줌 - 횟수가 같으면 사전순으로 골라 회차마다 바뀌지 않게 함
    private List<SearchKeywordDocument> toDocuments(Map<String, Double> counts, long aggregatedAt) {
        Comparator<Map.Entry<String, Double>> mostSearchedFirst = Map.Entry.<String, Double>comparingByValue().reversed()
                .thenComparing(Map.Entry.<String, Double>comparingByKey());

        return counts.entrySet().stream()
                .collect(Collectors.groupingBy(entry -> SearchKeywordDocument.keyOf(entry.getKey())))
                .values().stream()
                .filter(variants -> popularity(variants) >= MIN_POPULARITY)
                .map(variants -> SearchKeywordDocument.of(
                        variants.stream().min(mostSearchedFirst).orElseThrow().getKey(),
                        popularity(variants),
                        aggregatedAt))
                .toList();
    }

    private double popularity(List<Map.Entry<String, Double>> variants) {
        return variants.stream().mapToDouble(Map.Entry::getValue).sum();
    }
}
