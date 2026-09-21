package com.park.ecommerce.reservation.domain;

import com.park.ecommerce.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 주문 1건의 재고 선점 - 선점 시점에 차감한 상품별 수량과 결제 가능 기한(만료 시각)을 기록
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class StockReservation extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String orderNo;

    // 엔티티 UPDATE에서 제외 - 상태는 StockReservationRepository의 조건부 UPDATE로만 변경한다
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private StockReservationStatus status;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    // 라인은 선점과 생명주기가 같아 함께 저장
    @OneToMany(mappedBy = "reservation", cascade = CascadeType.PERSIST)
    private List<StockReservationLine> lines = new ArrayList<>();

    @Builder
    private StockReservation(String orderNo, LocalDateTime expiresAt) {
        this.orderNo = orderNo;
        this.expiresAt = expiresAt;
        this.status = StockReservationStatus.RESERVED;
    }

    public void addLine(Long productId, Integer quantity) {
        lines.add(new StockReservationLine(this, productId, quantity));
    }

    public Map<Long, Integer> quantities() {
        return lines.stream()
                .collect(Collectors.toMap(StockReservationLine::getProductId, StockReservationLine::getQuantity));
    }

    public boolean isConfirmed() {
        return status == StockReservationStatus.CONFIRMED;
    }
}
