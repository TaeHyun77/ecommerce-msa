package com.park.ecommerce.inbound.receipt;

import com.park.ecommerce.inbound.expectation.InboundExpectation;
import com.park.ecommerce.inbound.expectation.InboundExpectationRepository;
import com.park.ecommerce.inbound.expectation.ReceivedQuantity;
import com.park.ecommerce.exception.inbound.InboundErrorCode;
import com.park.ecommerce.exception.inbound.InboundException;
import com.park.ecommerce.product.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

// 입고 확정 - 검수된 양품 수량만 재고에 반영
@Slf4j
@Service
@RequiredArgsConstructor
public class InboundReceiptService {
    private final InboundExpectationRepository inboundExpectationRepository;
    private final ProductService productService;

    @Transactional
    public void receive(InboundReceiptRequest request) {
        if (request.hasDuplicateProduct()) {
            throw new InboundException(InboundErrorCode.DUPLICATE_LINE_PRODUCT);
        }

        // 비관적 락
        InboundExpectation expectation = inboundExpectationRepository.findByAsnNoForUpdate(request.asnNo())
                .orElseThrow(() -> new InboundException(InboundErrorCode.EXPECTATION_NOT_FOUND));

        if (expectation.isReceivedWith(request.receiptNo())) {
            log.info("[입고 확정] 이미 반영된 확정 재수신 무시 receiptNo={}, asnNo={}", request.receiptNo(), request.asnNo());
            return;
        }

        expectation.receive(request.receiptNo(), toQuantities(request), LocalDateTime.now());

        expectation.getLines().stream()
                .filter(line -> line.getAcceptedQuantity() > 0)
                .forEach(line -> productService.increaseStock(line.getProductId(), line.getAcceptedQuantity()));

        log.info("[입고 확정] 재고 반영 완료 receiptNo={}, asnNo={}", request.receiptNo(), request.asnNo());
    }

    private static Map<String, ReceivedQuantity> toQuantities(InboundReceiptRequest request) {
        return request.lines().stream()
                .collect(Collectors.toMap(
                        InboundReceiptRequest.Line::productCode,
                        line -> new ReceivedQuantity(line.acceptedQuantity(), line.rejectedQuantity())
                ));
    }
}
