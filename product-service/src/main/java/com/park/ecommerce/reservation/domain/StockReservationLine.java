package com.park.ecommerce.reservation.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 선점 한 건(주문 한 건) 안의 상품별 개별 품목
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class StockReservationLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private StockReservation reservation;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    StockReservationLine(StockReservation reservation, Long productId, Integer quantity) {
        this.reservation = reservation;
        this.productId = productId;
        this.quantity = quantity;
    }
}
