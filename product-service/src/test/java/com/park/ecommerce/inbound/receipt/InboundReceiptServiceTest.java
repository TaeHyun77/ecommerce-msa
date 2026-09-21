package com.park.ecommerce.inbound.receipt;

import com.park.ecommerce.inbound.expectation.InboundExpectation;
import com.park.ecommerce.inbound.expectation.InboundExpectationRepository;
import com.park.ecommerce.inbound.expectation.ReceivedQuantity;
import com.park.ecommerce.exception.InboundErrorCode;
import com.park.ecommerce.exception.InboundException;
import com.park.ecommerce.product.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class InboundReceiptServiceTest {
    @Mock
    private InboundExpectationRepository inboundExpectationRepository;

    @Mock
    private ProductService productService;

    @InjectMocks
    private InboundReceiptService inboundReceiptService;

    @Test
    @DisplayName("양품이 있는 품목만 양품 수량만큼 재고를 늘린다")
    void increasesStockByAcceptedQuantity() {
        given(inboundExpectationRepository.findByAsnNoForUpdate("ASN-0001")).willReturn(Optional.of(expectation()));

        inboundReceiptService.receive(request("RCV-0001", List.of(
                new InboundReceiptRequest.Line("SKU-0001", 195, 5),
                new InboundReceiptRequest.Line("SKU-0003", 0, 50)
        )));

        verify(productService).increaseStock(1L, 195);
        verifyNoMoreInteractions(productService);
    }

    @Test
    @DisplayName("같은 확정 번호로 다시 오면 재고를 다시 늘리지 않는다")
    void ignoresSameReceipt() {
        InboundExpectation expectation = expectation();
        expectation.receive("RCV-0001", Map.of("SKU-0001", new ReceivedQuantity(195, 5)), LocalDateTime.now());
        given(inboundExpectationRepository.findByAsnNoForUpdate("ASN-0001")).willReturn(Optional.of(expectation));

        inboundReceiptService.receive(request("RCV-0001", List.of(new InboundReceiptRequest.Line("SKU-0001", 195, 5))));

        verify(productService, never()).increaseStock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("다른 확정 번호로 이미 완료된 입고 예정이면 예외가 발생하고 재고를 늘리지 않는다")
    void rejectsAnotherReceipt() {
        InboundExpectation expectation = expectation();
        expectation.receive("RCV-0001", Map.of("SKU-0001", new ReceivedQuantity(195, 5)), LocalDateTime.now());
        given(inboundExpectationRepository.findByAsnNoForUpdate("ASN-0001")).willReturn(Optional.of(expectation));

        assertThatThrownBy(() -> inboundReceiptService.receive(
                request("RCV-9999", List.of(new InboundReceiptRequest.Line("SKU-0001", 195, 5)))))
                .isInstanceOf(InboundException.class)
                .extracting("errorCode")
                .isEqualTo(InboundErrorCode.ALREADY_RECEIVED);
        verify(productService, never()).increaseStock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("입고 예정이 없으면 예외가 발생한다")
    void rejectsUnknownExpectation() {
        given(inboundExpectationRepository.findByAsnNoForUpdate("ASN-0001")).willReturn(Optional.empty());

        assertThatThrownBy(() -> inboundReceiptService.receive(
                request("RCV-0001", List.of(new InboundReceiptRequest.Line("SKU-0001", 195, 5)))))
                .isInstanceOf(InboundException.class)
                .extracting("errorCode")
                .isEqualTo(InboundErrorCode.EXPECTATION_NOT_FOUND);
    }

    @Test
    @DisplayName("같은 상품이 여러 품목에 있으면 예외가 발생한다")
    void rejectsDuplicateLine() {
        assertThatThrownBy(() -> inboundReceiptService.receive(request("RCV-0001", List.of(
                new InboundReceiptRequest.Line("SKU-0001", 100, 0),
                new InboundReceiptRequest.Line("SKU-0001", 95, 5)
        ))))
                .isInstanceOf(InboundException.class)
                .extracting("errorCode")
                .isEqualTo(InboundErrorCode.DUPLICATE_LINE_PRODUCT);
    }

    private static InboundReceiptRequest request(String receiptNo, List<InboundReceiptRequest.Line> lines) {
        return new InboundReceiptRequest(receiptNo, "ASN-0001", lines);
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
