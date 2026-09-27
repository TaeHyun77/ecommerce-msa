package com.park.ecommerce.product;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

// 상품 변경 이벤트를 받아 최신 스냅샷으로 색인 문서를 덮어씁니다.
// 상품 서비스는 최소 한 번 발행하므로 같은 이벤트가 다시 와도 결과가 같도록 상품 id를 문서 id로 저장
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductIndexConsumer {
    private final ProductDocumentRepository productDocumentRepository;
    private final JsonMapper jsonMapper;

    @KafkaListener(topics = "${search.product.topic}", groupId = "search-service")
    public void index(String message) {
        ProductChangedEvent event = jsonMapper.readValue(message, ProductChangedEvent.class);
        productDocumentRepository.save(ProductDocument.from(event));
        log.info("[상품 색인] productId={}, status={}", event.productId(), event.status());
    }
}
