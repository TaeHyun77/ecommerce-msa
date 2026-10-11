package com.park.ecommerce.search.suggestion;

import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.park.ecommerce.text.JamoConverter;
import com.park.ecommerce.text.WordStarts;

import java.util.Arrays;
import java.util.stream.Collectors;

// 자동완성 후보 조건과 단계 점수 - 상품명·검색어 인덱스가 같은 쿼리를 써야 두 결과의 점수를 그대로 비교해 합칠 수 있음
// 순서: 단어 시작부터 정확히 이어짐 → 단어 시작부터 이어지되 마지막 글자를 치는 중 → 단어 중간부터 이어짐 → 단어가 흩어져 있음
// 점수는 고정 가산점(누적 7000/3000/1000/0, 초성 입력은 모두 1)으로만 정해 인덱스마다 다른 단어 통계가 섞이지 않게 함
public final class SuggestionQuery {
    // 색인할 때 자모를 최대 20개까지 잘라 저장했으니 검색할 때도 최대 20개까지만 사용
    private static final int MAX_NGRAM = 20;

    // 단계 가산점 - 앞 단계 후보는 뒤 단계 조건도 함께 만족해 가산점이 합산되므로, 앞 단계가 뒤 단계 합보다 크도록 두 배씩 둠
    private static final float EXACT_WORD_START_BOOST = 4000f;
    private static final float TYPING_WORD_START_BOOST = 2000f;
    private static final float INSIDE_WORD_BOOST = 1000f;

    private SuggestionQuery() {}

    public static Query of(String keyword) {
        if (JamoConverter.isChosungOnly(keyword)) {
            String chosung = JamoConverter.toChosung(keyword);
            return Query.of(q -> q.prefix(p -> p.field("nameChosungWordStart").value(chosung)));
        }

        String jamo = JamoConverter.toJamo(keyword);
        String tokens = truncateTokens(jamo);
        String noSpace = truncate(jamo.replaceAll("\\s+", ""));
        String compact = WordStarts.compact(keyword);
        String wordStart = truncate(compact);
        String jamoWordStart = truncate(JamoConverter.toJamo(compact));

        return Query.of(q -> q.bool(b -> b
                // 입력한 단어가 모두 들어 있거나(어순 무관), 붙여 쓴 입력이 이름에 이어져 있거나, 어떤 단어 시작부터 이어지면 후보
                // 단어 시작 조건은 "고소&아삭한"처럼 기호로 붙은 이름을 "고소아삭"으로 찾기 위해 후보 조건에도 둔다
                // 점수에 넣지 않음 - 넣으면 입력이 여러 번 나오는 긴 이름이 앞서므로, 점수는 아래 단계 가산점으로만 정함
                .filter(f -> f.bool(c -> c
                        .should(s -> s.match(match -> match.field("nameJamo").query(tokens).operator(Operator.And)))
                        .should(s -> s.match(match -> match.field("nameJamoNoSpace").query(noSpace)))
                        .should(s -> s.match(match -> match.field("nameJamoWordStart").query(jamoWordStart)))
                        .minimumShouldMatch("1")))
                .should(s -> s.constantScore(c -> c
                        .filter(f -> f.match(match -> match.field("nameWordStart").query(wordStart)))
                        .boost(EXACT_WORD_START_BOOST)))
                // 자모로 비교하면 "고"가 "곰", "골"의 앞부분이 되어 치는 중인 글자까지 단어 시작으로 잡힘
                .should(s -> s.constantScore(c -> c
                        .filter(f -> f.match(match -> match.field("nameJamoWordStart").query(jamoWordStart)))
                        .boost(TYPING_WORD_START_BOOST)))
                .should(s -> s.constantScore(c -> c
                        .filter(f -> f.match(match -> match.field("nameJamoNoSpace").query(noSpace)))
                        .boost(INSIDE_WORD_BOOST)))));
    }

    // n-gram 최대 길이보다 긴 조각은 사전에 없기에 아무것도 찾지 못하므로 앞부분만 사용
    private static String truncateTokens(String jamo) {
        return Arrays.stream(jamo.split("\\s+"))
                .map(SuggestionQuery::truncate)
                .collect(Collectors.joining(" "));
    }

    // 검색어가 n-gram 최대 길이인 20자를 넘지 않도록 앞부분만 자름
    private static String truncate(String token) {
        return token.length() > MAX_NGRAM ? token.substring(0, MAX_NGRAM) : token;
    }
}
