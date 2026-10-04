package com.park.ecommerce.product;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;
import org.springframework.data.elasticsearch.annotations.WriteTypeHint;

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

    @Field(type = FieldType.Text, analyzer = "korean")
    private String name;

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
