package com.park.ecommerce.inbound;

import com.park.ecommerce.exception.inbound.InboundErrorCode;
import com.park.ecommerce.exception.inbound.InboundException;
import com.park.ecommerce.inbound.dto.InboundRequest;
import com.park.ecommerce.inbound.dto.InboundResponse;
import com.park.ecommerce.product.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

// 입고 예정서 수신 - 예정서 저장, 품목별 재고 반영, 색인 기록을 한 트랜잭션에서 처리
// 실제 WMS가 없어 입고 확정 단계를 두지 않고 예정서를 받은 즉시 반영하며, 반영하지 못한 품목은 실패로 남기고 나머지는 진행
@Slf4j
@Service
@RequiredArgsConstructor
public class InboundService {
    private static final String NOT_REGISTERED_PRODUCT = "등록되지 않은 상품입니다.";

    private final InboundRepository inboundRepository;
    private final ProductService productService;

    // 입고 예정서를 저장하면서 품목별 재고를 한 트랜잭션에서 반영
    // - 처음 받은 예정서 : 저장과 함께 모든 품목을 반영
    // - 다시 받은 예정서(공급사 재전송) : 완료되지 않았다면 반영하지 못한 품목만 다시 처리하고, 완료됐다면 현재 결과만 응답
    @Transactional
    public InboundResponse receive(InboundRequest request) {
        if (request.hasDuplicateProduct()) { // 입고 예정서에 중복된 상품 있는지 검수
            throw new InboundException(InboundErrorCode.DUPLICATE_LINE_PRODUCT);
        }

        // 같은 예정서가 동시에 다시 들어와도 한 요청만 재고를 반영하도록 행을 잠금
        Optional<Inbound> existing = inboundRepository.findByAsnNoForUpdate(request.asnNo());

        // 공급사의 재전송 - 처음 받은 내용을 기준으로 아직 반영하지 못한 품목만 다시 처리
        if (existing.isPresent()) {
            Inbound inbound = existing.get();
            if (!inbound.isCompleted()) reflect(inbound);

            return InboundResponse.from(inbound);
        }

        Inbound inbound = request.toEntity();
        reflect(inbound);
        inboundRepository.save(inbound);
        return InboundResponse.from(inbound);
    }

    // 아직 반영되지 않은 입고 품목을 상품 등록 여부로 나눠서, 등록된 상품은 재고에 반영하고 등록되지 않은 상품은 실패로 표시한 뒤 입고의 완료 여부를 정함
    private void reflect(Inbound inbound) {
        List<InboundLine> targets = inbound.unreceivedLines();

        // 처리해야 할 입고 품목들의 상품코드를 한 번에 DB에서 조회해서 (상품 코드, 상품 id) Map으로 만듦
        Map<String, Long> productIds = productService.findProductIdsByCodes(
                targets.stream().map(InboundLine::getProductCode).toList()
        );

        for (InboundLine line : targets) {
            Long productId = productIds.get(line.getProductCode());

            // 등록되지 않은 상품이라면
            if (productId == null) {
                line.markFailed(NOT_REGISTERED_PRODUCT);
                continue;
            }
            productService.receiveStock(productId, line.getQuantity()); // 재고 증가
            line.markReceived(productId);
        }
        inbound.refreshStatus();

        log.info("[입고] 예정서 처리 asnNo={}, 상태={}, 반영 대상={}건", inbound.getAsnNo(), inbound.getStatus(), targets.size());
    }
}
