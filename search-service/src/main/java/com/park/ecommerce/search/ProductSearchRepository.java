package com.park.ecommerce.search;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import com.park.ecommerce.product.ProductDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Repository;

import java.util.List;

// 상품 검색 ES 쿼리 - 검색어로 판매중 상품의 식별자만 관련도순으로 찾음
// 관련도는 문구 일치 → 모든 단어 포함 → 일부 단어 포함 단계로 나누고, 3단계에서는 일치한 단어가 희소할수록 앞에 두도록 함
@Repository
@RequiredArgsConstructor
public class ProductSearchRepository {
    private static final String ON_SALE = "ON_SALE";
    private static final float TIER_BOOST = 1000f;

    private final ElasticsearchOperations elasticsearchOperations;

    public ProductSearchHits search(String keyword, int page, int size) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> b
                        // 형태소 토큰 중 하나만 일치해도 찾음(OR) - 점수는 일치한 단어의 IDF 합이라 희소한 단어(핵심어)를 포함할수록 높음
                        .must(m -> m.match(match -> match.field("name").query(keyword)))
                        // 모든 단어 포함 - 문구 일치는 이 조건도 만족하므로 두 가산점을 모두 받아 가장 앞에 옴
                        .should(s -> s.constantScore(c -> c
                                .filter(f -> f.match(match -> match.field("name").query(keyword).operator(Operator.And)))
                                .boost(TIER_BOOST)))
                        // 문구 일치 - 단어가 검색어와 같은 순서로 붙어 있음 ("고등어구이"도 고등어, 구이 연속 토큰이라 포함)
                        .should(s -> s.constantScore(c -> c
                                .filter(f -> f.matchPhrase(phrase -> phrase.field("name").query(keyword)))
                                .boost(TIER_BOOST)))
                        .filter(f -> f.term(t -> t.field("status").value(ON_SALE)))))
                .withSort(s -> s.score(score -> score.order(SortOrder.Desc)))
                // 관련도가 같은 상품의 순서를 고정해 페이지를 넘길 때 중복/누락이 생기지 않도록 함 - 식별자가 클수록 최근 등록
                .withSort(s -> s.field(field -> field.field("productId").order(SortOrder.Desc)))
                .withPageable(PageRequest.of(page, size))
                .withTrackTotalHits(true) // 기본값이면 1만 건을 넘는 전체 건수를 정확히 세지 않음
                .build();

        SearchHits<ProductDocument> hits = elasticsearchOperations.search(query, ProductDocument.class);
        List<Long> productIds = hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(ProductDocument::getProductId)
                .toList();

        return new ProductSearchHits(productIds, hits.getTotalHits());
    }
}
