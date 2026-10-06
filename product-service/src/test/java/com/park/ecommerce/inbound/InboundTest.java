package com.park.ecommerce.inbound;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;

class InboundTest {
    @Test
    @DisplayName("새 예정서는 미완료 상태이고 모든 품목이 대기 상태다")
    void startsIncompleteWithPendingLines() {
        Inbound inbound = inbound();

        assertThat(inbound.getStatus()).isEqualTo(InboundStatus.INCOMPLETE);
        assertThat(inbound.getLines()).extracting(InboundLine::getStatus)
                .containsOnly(InboundLineStatus.PENDING);
    }

    @Test
    @DisplayName("모든 품목이 반영되면 완료 상태가 된다")
    void completesWhenAllLinesReceived() {
        Inbound inbound = inbound();
        inbound.getLines().get(0).markReceived(1L);
        inbound.getLines().get(1).markReceived(2L);

        inbound.refreshStatus();

        assertThat(inbound.getStatus()).isEqualTo(InboundStatus.COMPLETED);
        assertThat(inbound.unreceivedLines()).isEmpty();
    }

    @Test
    @DisplayName("실패한 품목이 있으면 미완료 상태로 남고, 반영되지 않은 품목만 다시 처리 대상이 된다")
    void staysIncompleteWithFailedLine() {
        Inbound inbound = inbound();
        inbound.getLines().get(0).markReceived(1L);
        inbound.getLines().get(1).markFailed("등록되지 않은 상품입니다.");

        inbound.refreshStatus();

        assertThat(inbound.getStatus()).isEqualTo(InboundStatus.INCOMPLETE);
        assertThat(inbound.unreceivedLines()).extracting(InboundLine::getProductCode)
                .containsExactly("SKU-0002");
        assertThat(inbound.getLines().get(1).getFailReason()).isEqualTo("등록되지 않은 상품입니다.");
    }

    @Test
    @DisplayName("실패했던 품목이 나중에 반영되면 실패 사유가 지워진다")
    void clearsFailReasonWhenReceived() {
        InboundLine line = inbound().getLines().get(0);
        line.markFailed("등록되지 않은 상품입니다.");

        line.markReceived(1L);

        assertThat(line.getStatus()).isEqualTo(InboundLineStatus.RECEIVED);
        assertThat(line.getFailReason()).isNull();
        assertThat(line.getProductId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("실패 사유가 500자를 넘으면 잘라서 저장한다")
    void truncatesLongFailReason() {
        InboundLine line = inbound().getLines().get(0);

        line.markFailed("가".repeat(600));

        assertThat(line.getFailReason()).hasSize(500);
    }

    private static Inbound inbound() {
        Inbound inbound = Inbound.builder()
                .asnNo("ASN-0001")
                .supplierCode("SUP-001")
                .build();
        inbound.addLine("SKU-0001", 200);
        inbound.addLine("SKU-0002", 50);
        return inbound;
    }
}
