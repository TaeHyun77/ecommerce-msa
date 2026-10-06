package com.park.ecommerce.inbound;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class InboundLine {
    private static final int FAIL_REASON_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inbound_id", nullable = false)
    private Inbound inbound;

    private Long productId; // 미등록 상품이면 처리 전까지 null

    @Column(nullable = false)
    private String productCode;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InboundLineStatus status;

    // 실패 전에는 null
    @Column(length = FAIL_REASON_MAX_LENGTH)
    private String failReason;

    InboundLine(Inbound inbound, String productCode, Integer quantity) {
        this.inbound = inbound;
        this.productCode = productCode;
        this.quantity = quantity;
        this.status = InboundLineStatus.PENDING;
    }

    public void markReceived(Long productId) {
        this.productId = productId;
        this.status = InboundLineStatus.RECEIVED;
        this.failReason = null;
    }

    public void markFailed(String reason) {
        this.status = InboundLineStatus.FAILED;
        this.failReason = reason.length() <= FAIL_REASON_MAX_LENGTH ? reason : reason.substring(0, FAIL_REASON_MAX_LENGTH);
    }

    public boolean isReceived() {
        return status == InboundLineStatus.RECEIVED;
    }
}
