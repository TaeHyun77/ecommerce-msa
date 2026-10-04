package com.park.ecommerce.search.suggestion;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch.core.search.FieldCollapse;
import com.park.ecommerce.product.ProductDocument;
import com.park.ecommerce.text.JamoConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

// 검색창 자동완성 후보 쿼리 - 입력한 글자가 이름에 들어 있는 판매중 상품의 이름을 이름이 입력으로 시작하는 순으로 찾음
@Repository
@RequiredArgsConstructor
public class ProductSuggestionRepository {
    private static final String ON_SALE = "ON_SALE";
    private static final int SUGGESTION_SIZE = 10;

    // ES에 상품명을 색인할 때 자모를 최대 20개까지 잘라서 저장
    // 색인할 때 최대 20개로 잘랐으니 검색할 때도 최대 20개까지만 사용
    private static final int MAX_NGRAM = 20;
    private static final float PREFIX_BOOST = 10f; // 상품명이 입력으로 시작하는 경우 점수를 크게 올림

    private final ElasticsearchOperations elasticsearchOperations; // Java에서 Elasticsearch에 검색을 요청하는 통로

    // 사용자가 입력한 검색어를 기준으로 판매 중인 상품명의 자동완성 후보를 최대 10개 반환
    public List<String> suggest(String keyword) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> {
                    b.filter(f -> f.term(t -> t.field("status").value(ON_SALE)));
                    if (JamoConverter.isChosungOnly(keyword)) {
                        String chosung = JamoConverter.toChosung(keyword);
                        return b.must(m -> m.prefix(p -> p.field("nameChosung").value(chosung)));
                    }

                    String jamo = JamoConverter.toJamo(keyword);
                    String tokens = truncateTokens(jamo);
                    String noSpace = truncate(jamo.replaceAll("\\s+", ""));
                    // 입력한 단어가 모두 들어 있거나(어순 무관), 붙여 쓴 입력이 이름에 들어 있으면 후보
                    b.must(m -> m.bool(c -> c
                            .should(s -> s.match(match -> match.field("nameJamo").query(tokens).operator(Operator.And)))
                            .should(s -> s.match(match -> match.field("nameJamoNoSpace").query(noSpace)))
                            .minimumShouldMatch("1")));
                    return b.should(s -> s.prefix(p -> p.field("nameJamoFull").value(jamo).boost(PREFIX_BOOST)));
                }))
                .withSort(s -> s.score(score -> score.order(SortOrder.Desc)))
                .withSort(s -> s.field(field -> field.field("productId").order(SortOrder.Desc)))
                .withFieldCollapse(FieldCollapse.of(c -> c.field("nameJamoFull"))) // 같은 이름은 점수가 가장 높은 한 건만
                .withPageable(PageRequest.of(0, SUGGESTION_SIZE))
                .build();

        return elasticsearchOperations.search(query, ProductDocument.class).getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(ProductDocument::getName)
                .toList();
    }

    // n-gram 최대 길이보다 긴 조각은 사전에 없기에 아무것도 찾지 못하므로 앞부분만 사용
    private String truncateTokens(String jamo) {
        return Arrays.stream(jamo.split("\\s+"))
                .map(this::truncate)
                .collect(Collectors.joining(" "));
    }

    // 검색어가 n-gram 최대 길이인 20자를 넘지 않도록 앞부분만 자름
    private String truncate(String token) {
        return token.length() > MAX_NGRAM ? token.substring(0, MAX_NGRAM) : token;
    }
}
