package com.park.ecommerce.inbound.expectation;

import com.park.ecommerce.exception.inbound.InboundErrorCode;
import com.park.ecommerce.exception.inbound.InboundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class InboundExpectationTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 22, 9, 30);

    @Test
    @DisplayName("입고 확정하면 품목별 양품·불량이 기록되고 완료 상태가 된다")
    void receives() {
        InboundExpectation expectation = expectation();

        expectation.receive("RCV-0001", Map.of(
                "SKU-0001", new ReceivedQuantity(195, 5),
                "SKU-0003", new ReceivedQuantity(50, 0)
        ), NOW);

        assertThat(expectation.getStatus()).isEqualTo(InboundExpectationStatus.COMPLETED);
        assertThat(expectation.getReceiptNo()).isEqualTo("RCV-0001");
        assertThat(expectation.getCompletedAt()).isEqualTo(NOW);
        assertThat(expectation.getLines())
                .extracting(InboundExpectationLine::getProductCode, InboundExpectationLine::getAcceptedQuantity, InboundExpectationLine::getRejectedQuantity)
                .containsExactly(
                        tuple("SKU-0001", 195, 5),
                        tuple("SKU-0003", 50, 0)
                );
    }

    @Test
    @DisplayName("확정에서 빠진 품목은 미입고(양품 0, 불량 0)로 기록된다")
    void recordsMissingLineAsNotReceived() {
        InboundExpectation expectation = expectation();

        expectation.receive("RCV-0001", Map.of("SKU-0001", new ReceivedQuantity(200, 0)), NOW);

        InboundExpectationLine missingLine = expectation.getLines().get(1);
        assertThat(missingLine.getAcceptedQuantity()).isZero();
        assertThat(missingLine.getRejectedQuantity()).isZero();
    }

    @Test
    @DisplayName("입고 예정에 없는 상품을 확정하면 예외가 발생한다")
    void rejectsUnexpectedProduct() {
        InboundExpectation expectation = expectation();

        assertThatThrownBy(() -> expectation.receive("RCV-0001", Map.of("SKU-9999", new ReceivedQuantity(1, 0)), NOW))
                .isInstanceOf(InboundException.class)
                .extracting("errorCode")
                .isEqualTo(InboundErrorCode.UNEXPECTED_RECEIPT_PRODUCT);
    }

    @Test
    @DisplayName("양품과 불량의 합이 예정 수량을 넘으면 예외가 발생한다")
    void rejectsExceededQuantity() {
        InboundExpectation expectation = expectation();

        assertThatThrownBy(() -> expectation.receive("RCV-0001", Map.of("SKU-0001", new ReceivedQuantity(200, 1)), NOW))
                .isInstanceOf(InboundException.class)
                .extracting("errorCode")
                .isEqualTo(InboundErrorCode.RECEIPT_QUANTITY_EXCEEDED);
    }

    @Test
    @DisplayName("같은 확정 번호로 완료된 입고 예정은 재수신으로 판별된다")
    void detectsSameReceipt() {
        InboundExpectation expectation = expectation();
        expectation.receive("RCV-0001", Map.of("SKU-0001", new ReceivedQuantity(200, 0)), NOW);

        assertThat(expectation.isReceivedWith("RCV-0001")).isTrue();
        assertThat(expectation.isReceivedWith("RCV-9999")).isFalse();
    }

    @Test
    @DisplayName("이미 완료된 입고 예정을 다른 확정 번호로 확정하면 예외가 발생한다")
    void rejectsAnotherReceipt() {
        InboundExpectation expectation = expectation();
        expectation.receive("RCV-0001", Map.of("SKU-0001", new ReceivedQuantity(200, 0)), NOW);

        assertThatThrownBy(() -> expectation.receive("RCV-9999", Map.of("SKU-0001", new ReceivedQuantity(200, 0)), NOW))
                .isInstanceOf(InboundException.class)
                .extracting("errorCode")
                .isEqualTo(InboundErrorCode.ALREADY_RECEIVED);
    }

    private static InboundExpectation expectation() {
        InboundExpectation expectation = InboundExpectation.builder()
                .asnNo("ASN-0001")
                .supplierCode("SUP-001")
                .expectedArrivalAt(LocalDateTime.of(2026, 9, 22, 6, 0))
                .build();
        expectation.addLine(1L, "SKU-0001", 200);
        expectation.addLine(3L, "SKU-0003", 50);
        return expectation;
    }
}
