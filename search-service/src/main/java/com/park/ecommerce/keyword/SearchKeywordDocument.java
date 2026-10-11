package com.park.ecommerce.keyword;

import com.park.ecommerce.search.suggestion.SuggestionDocument;
import com.park.ecommerce.text.WordStarts;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;
import org.springframework.data.elasticsearch.annotations.WriteTypeHint;

// 자동완성 검색어 색인 문서 - Redis 검색 횟수로 주기마다 다시 계산해 덮어쓰는 파생 데이터
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Document(indexName = "search_keywords", writeTypeHint = WriteTypeHint.FALSE)
@Setting(settingPath = "elasticsearch/product-settings.json") // 상품 인덱스와 같은 분석기를 써야 같은 입력이 같은 토큰으로 매칭됨
public class SearchKeywordDocument extends SuggestionDocument {
    @Id
    private String id; // 표기만 다른 검색어를 한 문서로 합치는 키

    @Field(type = FieldType.Keyword)
    private String name; // 후보로 보여줄 표기 - 같은 키의 표기 중 가장 많이 검색된 것

    @Field(type = FieldType.Double)
    private Double popularity; // 최근 7일 검색 횟수를 하루 지날 때마다 0.9배 해서 더한 값

    @Field(type = FieldType.Long)
    private Long aggregatedAt; // 이 문서를 쓴 집계 회차의 시각(epoch ms) - 이번 회차에 쓰지 않은 문서를 지우는 기준

    private SearchKeywordDocument(String name, double popularity, long aggregatedAt) {
        super(name);
        this.id = keyOf(name);
        this.name = name;
        this.popularity = popularity;
        this.aggregatedAt = aggregatedAt;
    }

    public static SearchKeywordDocument of(String name, double popularity, long aggregatedAt) {
        return new SearchKeywordDocument(name, popularity, aggregatedAt);
    }

    // 띄어쓰기·기호·대소문자만 다른 표기를 같은 검색어로 봄 - 중복 제거, 문서 id, 후보 병합에 같은 기준을 씀
    public static String keyOf(String keyword) {
        return WordStarts.compact(keyword);
    }
}
