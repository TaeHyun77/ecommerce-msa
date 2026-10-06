package com.park.ecommerce.inbound;

import com.park.ecommerce.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

// 입고 예정서 ( 공급사가 보낸다고 가정 )
// 받은 즉시 품목별로 재고에 반영하고, 반영하지 못한 품목은 사유와 함께 남기도록 함
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Inbound extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String asnNo; // 입고 예정서 식별 값

    @Column(nullable = false)
    private String supplierCode; // 공급사 식별 값

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InboundStatus status;

    // 라인은 예정서와 생명주기가 같아 함께 저장
    @OneToMany(mappedBy = "inbound", cascade = CascadeType.PERSIST)
    private List<InboundLine> lines = new ArrayList<>();

    @Builder
    private Inbound(String asnNo, String supplierCode) {
        this.asnNo = asnNo;
        this.supplierCode = supplierCode;
        this.status = InboundStatus.INCOMPLETE;
    }

    public void addLine(String productCode, Integer quantity) {
        lines.add(new InboundLine(this, productCode, quantity));
    }

    // 아직 재고에 반영되지 않은 품목 - 처음에는 전체, 재전송 시에는 실패했던 품목만
    public List<InboundLine> unreceivedLines() {
        return lines.stream().filter(line -> !line.isReceived()).toList();
    }

    public boolean isCompleted() {
        return status == InboundStatus.COMPLETED;
    }

    // 품목 처리를 마친 뒤 전체 반영 여부로 상태를 정함
    public void refreshStatus() {
        this.status = lines.stream().allMatch(InboundLine::isReceived)
                ? InboundStatus.COMPLETED
                : InboundStatus.INCOMPLETE;
    }
}
