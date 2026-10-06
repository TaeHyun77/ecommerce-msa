package com.park.ecommerce.inbound;

import com.park.ecommerce.inbound.dto.InboundRequest;
import com.park.ecommerce.product.ProductService;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 문제를 재현하기 위한 첫 구현(v1) - 품목별로 재고를 반영하다 실패하면 실패로 기록하고 넘어가 마지막에 한 번에 커밋한다.
 * 운영 코드가 아니라 InboundRollbackReproTest에서만 쓴다.
 */
public class NaiveInboundService {
    private final InboundRepository inboundRepository;
    private final ProductService productService;

    public NaiveInboundService(InboundRepository inboundRepository, ProductService productService) {
        this.inboundRepository = inboundRepository;
        this.productService = productService;
    }

    @Transactional
    public void receive(InboundRequest request) {
        Inbound inbound = request.toEntity();
        inboundRepository.save(inbound);

        Map<String, Long> productIds = productService.findProductIdsByCodes(
                inbound.getLines().stream().map(InboundLine::getProductCode).toList()
        );

        for (InboundLine line : inbound.getLines()) {
            Long productId = productIds.get(line.getProductCode());
            try {
                productService.receiveStock(productId, line.getQuantity()); // 미등록 상품은 id가 없어 재고 증가에서 예외가 난다
                line.markReceived(productId);
            } catch (Exception e) {
                line.markFailed("재고 반영 실패: " + e.getMessage()); // 실패는 기록하고 다음 품목으로 넘어간다
            }
        }
        inbound.refreshStatus();
    }
}
