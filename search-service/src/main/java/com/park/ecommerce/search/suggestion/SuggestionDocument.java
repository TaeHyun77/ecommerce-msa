package com.park.ecommerce.search.suggestion;

import com.park.ecommerce.text.JamoConverter;
import com.park.ecommerce.text.WordStarts;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.List;

// 자동완성 후보 문서(상품명, 수집 검색어)의 공통 매칭 필드
// 두 인덱스가 같은 입력에 같은 단계 점수를 내야 결과를 합칠 수 있으므로 필드 이름과 계산을 한곳에서 정함
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class SuggestionDocument {
    // 이름을 자모로 풀어 입력 중인 미완성 글자("고등ㅇ")도 일치하도록 함
    @Field(type = FieldType.Text, analyzer = "jamo_ngram", searchAnalyzer = "jamo_search")
    private String nameJamo;

    // 띄어쓰기 없이 붙여 쓴 입력("고등어구이")을 위해 공백을 뺀 이름 전체를 n-gram으로 자름
    @Field(type = FieldType.Text, analyzer = "jamo_ngram", searchAnalyzer = "jamo_search")
    private String nameJamoNoSpace;

    // 초성 검색("ㄱㄷㅇ") - 단어마다 그 단어부터 끝까지의 초성. 입력이 이 중 하나로 시작하면 후보
    @Field(type = FieldType.Keyword)
    private List<String> nameChosungWordStart;

    // 입력이 어느 단어 시작부터 이어지는지 판정. 단어마다 그 단어부터 끝까지 붙인 값의 앞부분(1~20자)을 색인
    @Field(type = FieldType.Text, analyzer = "word_start", searchAnalyzer = "keyword")
    private List<String> nameWordStart;

    // 위 값의 자모 버전 - 마지막 글자를 치는 중인 입력("고" → 곰, 골)도 단어 시작부터 이어진 것으로 판정
    @Field(type = FieldType.Text, analyzer = "word_start", searchAnalyzer = "keyword")
    private List<String> nameJamoWordStart;

    // 같은 단계 안에서 짧은 이름을 앞에 두기 위한 공백/기호를 뺀 글자 수
    @Field(type = FieldType.Integer)
    private Integer nameLength;

    protected SuggestionDocument(String name) {
        this.nameJamo = JamoConverter.toJamo(name);
        this.nameJamoNoSpace = nameJamo.replaceAll("\\s+", "");
        this.nameWordStart = WordStarts.of(name);
        this.nameJamoWordStart = nameWordStart.stream().map(JamoConverter::toJamo).toList();
        // 한글이 없는 단어("70g")부터 시작하면 초성이 비므로 뺌
        this.nameChosungWordStart = nameWordStart.stream()
                .map(JamoConverter::toChosung)
                .filter(chosung -> !chosung.isEmpty())
                .toList();
        this.nameLength = WordStarts.compact(name).length();
    }
}
