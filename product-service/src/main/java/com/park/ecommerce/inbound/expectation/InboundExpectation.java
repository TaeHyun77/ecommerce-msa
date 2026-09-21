package com.park.ecommerce.inbound.expectation;

import com.park.ecommerce.common.BaseTimeEntity;
import com.park.ecommerce.exception.inbound.InboundErrorCode;
import com.park.ecommerce.exception.inbound.InboundException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

// 검증을 마친 입고 예정 (우리 규격)
// 예정서 1건당 입고 확정은 1회만 받음
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class InboundExpectation extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String asnNo;

    @Column(nullable = false)
    private String supplierCode;

    @Column(nullable = false)
    private LocalDateTime expectedArrivalAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InboundExpectationStatus status;

    @Column(unique = true)
    private String receiptNo; // 입고 확정 번호 - 확정 전에는 null

    private LocalDateTime completedAt;

    // 라인은 입고 예정과 생명주기가 같아 함께 저장
    @OneToMany(mappedBy = "expectation", cascade = CascadeType.PERSIST)
    private List<InboundExpectationLine> lines = new ArrayList<>();

    @Builder
    private InboundExpectation(String asnNo, String supplierCode, LocalDateTime expectedArrivalAt) {
        this.asnNo = asnNo;
        this.supplierCode = supplierCode;
        this.expectedArrivalAt = expectedArrivalAt;
        this.status = InboundExpectationStatus.EXPECTED;
    }

    public void addLine(Long productId, String productCode, Integer expectedQuantity) {
        lines.add(new InboundExpectationLine(this, productId, productCode, expectedQuantity));
    }

    // WMS 재전송으로 같은 확정이 다시 들어온 경우 - 재고를 다시 반영하지 않도록 먼저 확인
    public boolean isReceivedWith(String receiptNo) {
        return status == InboundExpectationStatus.COMPLETED && Objects.equals(this.receiptNo, receiptNo);
    }

    // quantities : 상품코드별 검수 결과. 빠진 품목은 미입고(0/0)로 기록
    public void receive(String receiptNo, Map<String, ReceivedQuantity> quantities, LocalDateTime now) {
        if (status == InboundExpectationStatus.COMPLETED) {
            throw new InboundException(InboundErrorCode.ALREADY_RECEIVED);
        }
        validateProducts(quantities.keySet());

        lines.forEach(line -> line.receive(quantities.getOrDefault(line.getProductCode(), ReceivedQuantity.NOT_RECEIVED)));
        this.status = InboundExpectationStatus.COMPLETED;
        this.receiptNo = receiptNo;
        this.completedAt = now;
    }

    private void validateProducts(Set<String> receivedProductCodes) {
        Set<String> expectedProductCodes = lines.stream()
                .map(InboundExpectationLine::getProductCode)
                .collect(Collectors.toSet());
        if (!expectedProductCodes.containsAll(receivedProductCodes)) {
            throw new InboundException(InboundErrorCode.UNEXPECTED_RECEIPT_PRODUCT);
        }
    }
}
