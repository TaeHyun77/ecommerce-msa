package com.park.ecommerce.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 발행 전까지만 이벤트를 붙잡아 두는 대기열 - 발행에 성공하면 삭제되므로 남아 있는 행은 모두 미발행
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 발행 순서의 기준 - 같은 상품의 이벤트가 저장된 순서대로 나가도록

    @Column(nullable = false)
    private Long aggregateId; // 상품 id - Kafka 메시지 키로 써서 같은 상품의 이벤트가 한 파티션에서 순서대로 처리되도록

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload; // 상품 정보 json

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public OutboxEvent(Long aggregateId, String payload, LocalDateTime createdAt) {
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.createdAt = createdAt;
    }
}
