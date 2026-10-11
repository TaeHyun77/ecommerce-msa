package com.park.ecommerce.search.suggestion;

import co.elastic.clients.elasticsearch._types.SortOrder;
import com.park.ecommerce.keyword.SearchKeywordDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Repository;

import java.util.List;

// 검색창 자동완성 후보 쿼리 - 입력이 들어 있는 수집 검색어를 찾음 (후보 조건과 단계 점수는 상품명과 같은 SuggestionQuery)
@Repository
@RequiredArgsConstructor
public class KeywordSuggestionRepository {
    private static final int SUGGESTION_SIZE = 10;

    private final ElasticsearchOperations elasticsearchOperations;

    public List<ScoredSuggestion> suggest(String keyword) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(SuggestionQuery.of(keyword))
                .withSort(s -> s.score(score -> score.order(SortOrder.Desc)))
                // 같은 단계 안에서는 많이 검색된 검색어, 그다음 짧은 검색어를 앞에 둠
                .withSort(s -> s.field(field -> field.field("popularity").order(SortOrder.Desc)))
                .withSort(s -> s.field(field -> field.field("nameLength").order(SortOrder.Asc)))
                .withPageable(PageRequest.of(0, SUGGESTION_SIZE))
                .build();

        return elasticsearchOperations.search(query, SearchKeywordDocument.class).getSearchHits().stream()
                .map(hit -> new ScoredSuggestion(hit.getContent().getName(), hit.getScore()))
                .toList();
    }
}
