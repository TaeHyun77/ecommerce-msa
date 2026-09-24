package com.park.ecommerce.category;

import com.park.ecommerce.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 상위(예: 해산물) > 하위(예: 쭈꾸미·낙지·오징어) 2단계로 고정된 상품 분류
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Category extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private Long parentId; // 상위 카테고리면 null 값을 가짐

    @Builder
    private Category(String name, Long parentId) {
        this.name = name;
        this.parentId = parentId;
    }

    public boolean isTopLevel() {
        return parentId == null;
    }
}
