package com.park.ecommerce.search.suggestion;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.search.FieldCollapse;
import com.park.ecommerce.product.ProductDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Repository;

import java.util.List;

// 검색창 자동완성 후보 쿼리 - 입력이 이름에 들어 있는 판매중 상품의 이름을 찾음 (후보 조건과 단계 점수는 SuggestionQuery)
@Repository
@RequiredArgsConstructor
public class ProductSuggestionRepository {
    private static final String ON_SALE = "ON_SALE";
    private static final int SUGGESTION_SIZE = 10;

    private final ElasticsearchOperations elasticsearchOperations; // Java에서 Elasticsearch에 검색을 요청하는 통로

    // 사용자가 입력한 검색어를 기준으로 판매 중인 상품명의 자동완성 후보를 최대 10개 반환
    public List<ScoredSuggestion> suggest(String keyword) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> b
                        .filter(f -> f.term(t -> t.field("status").value(ON_SALE)))
                        .must(SuggestionQuery.of(keyword))))
                .withSort(s -> s.score(score -> score.order(SortOrder.Desc)))
                // 같은 단계 안에서는 짧은 이름이 더 깔끔한 후보이므로 앞에 두고, 길이도 같으면 최근 등록순
                .withSort(s -> s.field(field -> field.field("nameLength").order(SortOrder.Asc)))
                .withSort(s -> s.field(field -> field.field("productId").order(SortOrder.Desc)))
                .withFieldCollapse(FieldCollapse.of(c -> c.field("nameJamoFull"))) // 같은 이름은 정렬상 가장 앞선 한 건만
                .withPageable(PageRequest.of(0, SUGGESTION_SIZE))
                .build();

        return elasticsearchOperations.search(query, ProductDocument.class).getSearchHits().stream()
                .map(hit -> new ScoredSuggestion(hit.getContent().getName(), hit.getScore()))
                .toList();
    }
}
