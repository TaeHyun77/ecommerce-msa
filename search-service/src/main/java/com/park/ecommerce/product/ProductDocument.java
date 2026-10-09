package com.park.ecommerce.product;

import lombok.AccessLevel;
import lombok.Getter;
import com.park.ecommerce.text.JamoConverter;
import com.park.ecommerce.text.WordStarts;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;
import org.springframework.data.elasticsearch.annotations.WriteTypeHint;

import java.util.List;

// 상품 검색 색인 문서
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Document(indexName = "products", writeTypeHint = WriteTypeHint.FALSE) // 저장할 인덱스 이름을 정하고, 이 인덱스에 쓸모없는 _class 필드를 빼도록 함
@Setting(settingPath = "elasticsearch/product-settings.json") // @Document가 가리키는 인덱스(products)를 만들 때 적용할 설정을 지정
public class ProductDocument {
    @Id
    private String id; // 문서 id = 상품 id - 같은 상품 이벤트를 여러 번 받아도 한 문서를 덮어쓰도록

    // 검색 정렬의 동점 처리에 쓰는 숫자 필드 - @Id 필드는 Spring Data가 keyword(문자열)로 매핑해 숫자 순서로 정렬할 수 없어 따로 둔다
    @Field(type = FieldType.Long)
    private Long productId;

    // 길이 보정(짧은 이름 우대)을 끔 - 상품명 길이는 브랜드·용량 같은 부가 정보 차이라 관련도와 무관하므로 점수를 일치한 단어로만 정함
    // 기존 인덱스에 매핑 변경 API로 적용하면 이후 쓰기가 모두 실패함(ES 9.4 실측) - 인덱스를 새로 만들고 상품 이벤트를 다시 발행해야 함
    @Field(type = FieldType.Text, analyzer = "korean", norms = false)
    private String name;

    // 자동완성용 필드 - 이름을 자모로 풀어 입력 중인 미완성 글자("고등ㅇ")도 일치하도록 함
    @Field(type = FieldType.Text, analyzer = "jamo_ngram", searchAnalyzer = "jamo_search")
    private String nameJamo;

    // 띄어쓰기 없이 붙여 쓴 입력("고등어구이")을 위해 공백을 뺀 이름 전체를 n-gram으로 자름
    @Field(type = FieldType.Text, analyzer = "jamo_ngram", searchAnalyzer = "jamo_search")
    private String nameJamoNoSpace;

    // 이름이 입력으로 시작하는 상품에 가산점을 주고, 같은 이름을 하나로 합치는 기준으로 사용
    @Field(type = FieldType.Keyword)
    private String nameJamoFull;

    // 초성 검색("ㄱㄷㅇ") - 단어마다 그 단어부터 끝까지의 초성. 입력이 이 중 하나로 시작하면 후보
    @Field(type = FieldType.Keyword)
    private List<String> nameChosungWordStart;

    // 자동완성 - 입력이 어느 단어 시작부터 이어지는지 판정. 단어마다 그 단어부터 끝까지 붙인 값의 앞부분(1~20자)을 색인
    @Field(type = FieldType.Text, analyzer = "word_start", searchAnalyzer = "keyword")
    private List<String> nameWordStart;

    // 위 값의 자모 버전 - 마지막 글자를 치는 중인 입력("고" → 곰, 골)도 단어 시작부터 이어진 것으로 판정
    @Field(type = FieldType.Text, analyzer = "word_start", searchAnalyzer = "keyword")
    private List<String> nameJamoWordStart;

    // 자동완성의 같은 단계 안에서 짧은 이름을 앞에 두기 위한 공백/기호를 뺀 글자 수
    @Field(type = FieldType.Integer)
    private Integer nameLength;

    @Field(type = FieldType.Text, analyzer = "korean")
    private String brand;

    @Field(type = FieldType.Long)
    private Long categoryId;

    @Field(type = FieldType.Long)
    private Long parentCategoryId; // 상위 카테고리 필터를 하위 id 목록 없이 바로 걸기 위함

    @Field(type = FieldType.Keyword) // 문자열을 자르지 않고 값 전체를 하나의 토큰으로 저장
    private String status;

    @Field(type = FieldType.Integer)
    private Integer price;

    private ProductDocument(ProductChangedEvent event) {
        this.id = String.valueOf(event.productId());
        this.productId = event.productId();
        this.name = event.name();
        this.nameJamo = JamoConverter.toJamo(event.name());
        this.nameJamoNoSpace = nameJamo.replaceAll("\\s+", "");
        this.nameJamoFull = nameJamo;
        this.nameWordStart = WordStarts.of(event.name());
        this.nameJamoWordStart = nameWordStart.stream().map(JamoConverter::toJamo).toList();
        // 한글이 없는 단어("70g")부터 시작하면 초성이 비므로 뺌
        this.nameChosungWordStart = nameWordStart.stream()
                .map(JamoConverter::toChosung)
                .filter(chosung -> !chosung.isEmpty())
                .toList();
        this.nameLength = WordStarts.compact(event.name()).length();
        this.brand = event.brand();
        this.categoryId = event.categoryId();
        this.parentCategoryId = event.parentCategoryId();
        this.status = event.status();
        this.price = event.price();
    }

    public static ProductDocument from(ProductChangedEvent event) {
        return new ProductDocument(event);
    }
}
