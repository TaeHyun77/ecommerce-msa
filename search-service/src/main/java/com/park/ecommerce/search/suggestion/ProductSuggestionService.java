package com.park.ecommerce.search.suggestion;

import com.park.ecommerce.exception.SearchErrorCode;
import com.park.ecommerce.exception.SearchException;
import com.park.ecommerce.keyword.SearchKeywordDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 검색창 자동완성 - 수집 검색어와 판매중 상품명 후보를 일치 단계 순으로 합침 (ES만 조회)
@Service
@RequiredArgsConstructor
public class ProductSuggestionService {
    private static final int MAX_QUERY_LENGTH = 50;
    private static final int SUGGESTION_SIZE = 10;

    private final KeywordSuggestionRepository keywordSuggestionRepository;
    private final ProductSuggestionRepository productSuggestionRepository;

    // 각 출처에서 10개씩만 가져와도 충분함 - 출처 안의 순서를 유지하므로 출처 안 11위 이하는 앞에 같은 출처 후보 10개가 있어 들어올 수 없음
    public List<String> suggest(String query) {
        String keyword = normalize(query);

        List<ScoredSuggestion> candidates = new ArrayList<>(keywordSuggestionRepository.suggest(keyword));
        candidates.addAll(productSuggestionRepository.suggest(keyword));
        // List.sort는 안정 정렬이라 같은 단계면 앞에 넣은 검색어가 먼저 오고, 출처 안의 순서도 유지됨
        candidates.sort(Comparator.comparingDouble(ScoredSuggestion::score).reversed());

        // 띄어쓰기·기호만 다른 같은 이름은 앞선 후보 하나만 남김
        Set<String> seenKeys = new HashSet<>();
        return candidates.stream()
                .filter(candidate -> seenKeys.add(SearchKeywordDocument.keyOf(candidate.text())))
                .limit(SUGGESTION_SIZE)
                .map(ScoredSuggestion::text)
                .toList();
    }

    // 띄어쓰기만 다른 입력이 같은 후보를 내도록 공백을 정리
    private String normalize(String query) {
        String keyword = query.strip().replaceAll("\\s+", " ");
        if (keyword.isEmpty() || keyword.length() > MAX_QUERY_LENGTH) {
            throw new SearchException(SearchErrorCode.INVALID_SEARCH_QUERY);
        }
        return keyword;
    }
}
