package com.park.ecommerce.search;

import co.elastic.clients.elasticsearch._types.SortOrder;
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
@Repository
@RequiredArgsConstructor
public class ProductSearchRepository {
    private static final String ON_SALE = "ON_SALE";
    private final ElasticsearchOperations elasticsearchOperations;

    public ProductSearchHits search(String keyword, int page, int size) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> b
                        // 형태소 토큰 중 하나만 일치해도 찾고(OR), 많이 일치할수록 관련도 점수가 높아 앞에 오도록
                        .must(m -> m.match(match -> match.field("name").query(keyword)))
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
