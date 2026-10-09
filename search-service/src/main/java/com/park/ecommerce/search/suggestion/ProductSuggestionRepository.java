package com.park.ecommerce.search.suggestion;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch.core.search.FieldCollapse;
import com.park.ecommerce.product.ProductDocument;
import com.park.ecommerce.text.JamoConverter;
import com.park.ecommerce.text.WordStarts;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

// 검색창 자동완성 후보 쿼리 - 입력이 이름에 들어 있는 판매중 상품의 이름을 찾음
// 순서: 단어 시작부터 정확히 이어짐 → 단어 시작부터 이어지되 마지막 글자를 치는 중 → 단어 중간부터 이어짐 → 단어가 흩어져 있음
@Repository
@RequiredArgsConstructor
public class ProductSuggestionRepository {
    private static final String ON_SALE = "ON_SALE";
    private static final int SUGGESTION_SIZE = 10;

    // ES에 상품명을 색인할 때 자모를 최대 20개까지 잘라서 저장
    // 색인할 때 최대 20개로 잘랐으니 검색할 때도 최대 20개까지만 사용
    private static final int MAX_NGRAM = 20;

    // 단계 가산점 - 앞 단계 상품은 뒤 단계 조건도 함께 만족해 가산점이 합산되므로, 앞 단계가 뒤 단계 합보다 크도록 두 배씩 둠
    private static final float EXACT_WORD_START_BOOST = 4000f;
    private static final float TYPING_WORD_START_BOOST = 2000f;
    private static final float INSIDE_WORD_BOOST = 1000f;

    private final ElasticsearchOperations elasticsearchOperations; // Java에서 Elasticsearch에 검색을 요청하는 통로

    // 사용자가 입력한 검색어를 기준으로 판매 중인 상품명의 자동완성 후보를 최대 10개 반환
    public List<String> suggest(String keyword) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> {
                    b.filter(f -> f.term(t -> t.field("status").value(ON_SALE)));
                    if (JamoConverter.isChosungOnly(keyword)) {
                        String chosung = JamoConverter.toChosung(keyword);
                        return b.must(m -> m.prefix(p -> p.field("nameChosungWordStart").value(chosung)));
                    }

                    String jamo = JamoConverter.toJamo(keyword);
                    String tokens = truncateTokens(jamo);
                    String noSpace = truncate(jamo.replaceAll("\\s+", ""));
                    String compact = WordStarts.compact(keyword);
                    String wordStart = truncate(compact);
                    String jamoWordStart = truncate(JamoConverter.toJamo(compact));
                    // 입력한 단어가 모두 들어 있거나(어순 무관), 붙여 쓴 입력이 이름에 이어져 있거나, 어떤 단어 시작부터 이어지면 후보
                    // 단어 시작 조건은 "고소&아삭한"처럼 기호로 붙은 이름을 "고소아삭"으로 찾기 위해 후보 조건에도 둔다
                    // 점수에 넣지 않음 - 넣으면 입력이 여러 번 나오는 긴 이름이 앞서므로, 점수는 아래 단계 가산점으로만 정함
                    b.filter(f -> f.bool(c -> c
                            .should(s -> s.match(match -> match.field("nameJamo").query(tokens).operator(Operator.And)))
                            .should(s -> s.match(match -> match.field("nameJamoNoSpace").query(noSpace)))
                            .should(s -> s.match(match -> match.field("nameJamoWordStart").query(jamoWordStart)))
                            .minimumShouldMatch("1")));
                    return b
                            .should(s -> s.constantScore(c -> c
                                    .filter(f -> f.match(match -> match.field("nameWordStart").query(wordStart)))
                                    .boost(EXACT_WORD_START_BOOST)))
                            // 자모로 비교하면 "고"가 "곰", "골"의 앞부분이 되어 치는 중인 글자까지 단어 시작으로 잡힘
                            .should(s -> s.constantScore(c -> c
                                    .filter(f -> f.match(match -> match.field("nameJamoWordStart").query(jamoWordStart)))
                                    .boost(TYPING_WORD_START_BOOST)))
                            .should(s -> s.constantScore(c -> c
                                    .filter(f -> f.match(match -> match.field("nameJamoNoSpace").query(noSpace)))
                                    .boost(INSIDE_WORD_BOOST)));
                }))
                .withSort(s -> s.score(score -> score.order(SortOrder.Desc)))
                // 같은 단계 안에서는 짧은 이름이 더 깔끔한 후보이므로 앞에 두고, 길이도 같으면 최근 등록순
                .withSort(s -> s.field(field -> field.field("nameLength").order(SortOrder.Asc)))
                .withSort(s -> s.field(field -> field.field("productId").order(SortOrder.Desc)))
                .withFieldCollapse(FieldCollapse.of(c -> c.field("nameJamoFull"))) // 같은 이름은 정렬상 가장 앞선 한 건만
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
