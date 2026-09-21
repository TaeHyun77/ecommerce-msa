package com.park.ecommerce.inbound.expectation;

import com.park.ecommerce.exception.inbound.InboundErrorCode;
import com.park.ecommerce.exception.inbound.InboundException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class InboundExpectationLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expectation_id", nullable = false)
    private InboundExpectation expectation;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private String productCode; // 입고 확정 요청이 상품코드로 오기 때문에 함께 보관

    @Column(nullable = false)
    private Integer expectedQuantity;

    private Integer acceptedQuantity; // 확정 전에는 null

    private Integer rejectedQuantity; // 확정 전에는 null

    InboundExpectationLine(InboundExpectation expectation, Long productId, String productCode, Integer expectedQuantity) {
        this.expectation = expectation;
        this.productId = productId;
        this.productCode = productCode;
        this.expectedQuantity = expectedQuantity;
    }

    // 초과 입고분은 WMS가 회송 처리하고 확정 수량에 포함하지 않는다고 가정
    void receive(ReceivedQuantity quantity) {
        if (quantity.total() > expectedQuantity) {
            throw new InboundException(InboundErrorCode.RECEIPT_QUANTITY_EXCEEDED);
        }
        this.acceptedQuantity = quantity.accepted();
        this.rejectedQuantity = quantity.rejected();
    }
}
