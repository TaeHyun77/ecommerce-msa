package com.park.ecommerce.product;

import com.park.ecommerce.category.Category;
import com.park.ecommerce.category.CategoryRepository;
import com.park.ecommerce.outbox.OutboxEvent;
import com.park.ecommerce.outbox.OutboxEventRepository;
import com.park.ecommerce.product.dto.ProductCreateRequest;
import com.park.ecommerce.product.dto.ProductUpdateRequest;
import com.park.ecommerce.product.status.ProductStatus;
import com.park.ecommerce.product.status.StorageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 행 잠금 조회(SELECT ... FOR UPDATE)와 수정·이벤트 저장의 트랜잭션 처리는 실제 MySQL에서만 확인할 수 있어 컨테이너로 검증
@Testcontainers
@SpringBootTest(properties = { // 테스트 중 스케줄러 개입 방지
        "outbox.relay.poll-delay=1h"
})
class ProductUpdateIntegrationTest {
    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    @DisplayName("판매중인 상품을 수정하면 변경 내용이 저장되고, 상품코드·판매 상태·재고는 유지되며 색인 이벤트가 함께 저장된다")
    void updatesProductAndStoresEvent() {
        Category parent = categoryRepository.save(Category.builder().name("수산").build());
        Category sub = categoryRepository.save(Category.builder().name("생선").parentId(parent.getId()).build());
        Long productId = productRepository.save(new ProductCreateRequest(
                "SKU-U-001", "노르웨이 고등어", "컬리팜", null, StorageType.FROZEN, 3_000, null, sub.getId()
        ).toEntity()).getId();
        productService.receiveStock(productId, 5); // 판매를 시작하고 재고를 5개로 만든다
        outboxEventRepository.deleteAll(); // 판매 시작 이벤트는 비우고 수정 이벤트만 확인

        productService.update(productId, new ProductUpdateRequest(
                "수정된 노르웨이 고등어", "수정된 브랜드", "수정된 설명", StorageType.REFRIGERATED, 4_000, null, sub.getId()
        ));

        Product updated = productRepository.findById(productId).orElseThrow();
        assertThat(updated.getName()).isEqualTo("수정된 노르웨이 고등어");
        assertThat(updated.getPrice()).isEqualTo(4_000);
        assertThat(updated.getStorageType()).isEqualTo(StorageType.REFRIGERATED);
        assertThat(updated.getProductCode()).isEqualTo("SKU-U-001");
        assertThat(updated.getStatus()).isEqualTo(ProductStatus.ON_SALE);
        assertThat(updated.getStockQuantity()).isEqualTo(5);

        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getAggregateId()).isEqualTo(productId);
        assertThat(events.get(0).getPayload())
                .contains("수정된 노르웨이 고등어")
                .contains("\"parentCategoryId\":" + parent.getId());
    }
}
