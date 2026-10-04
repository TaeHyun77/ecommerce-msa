package com.park.ecommerce.search.suggestion;

import com.park.ecommerce.exception.SearchErrorCode;
import com.park.ecommerce.exception.SearchException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

// 검색창 자동완성 - 색인된 판매중 상품의 이름을 후보로 함 (ES만 조회)
@Service
@RequiredArgsConstructor
public class ProductSuggestionService {
    private static final int MAX_QUERY_LENGTH = 50;
    private final ProductSuggestionRepository productSuggestionRepository;

    public List<String> suggest(String query) {
        return productSuggestionRepository.suggest(normalize(query));
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
