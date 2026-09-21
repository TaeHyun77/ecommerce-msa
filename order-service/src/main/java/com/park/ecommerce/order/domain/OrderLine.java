package com.park.ecommerce.order.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 주문 품목 - 상품명과 단가는 주문 시점의 값을 스냅샷으로 보관 - 바뀌면 안되니까
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class OrderLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // product-service의 Product를 직접 참조하지 않고 식별자만 보관
    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private Integer unitPrice; // 주문 시점 판매가 (원)

    @Column(nullable = false)
    private Integer quantity;

    OrderLine(Order order, Long productId, String productName, Integer unitPrice, Integer quantity) {
        this.order = order;
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }
}
