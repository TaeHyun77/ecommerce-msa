package com.park.ecommerce.inbound;

import com.park.ecommerce.category.Category;
import com.park.ecommerce.category.CategoryRepository;
import com.park.ecommerce.inbound.dto.InboundRequest;
import com.park.ecommerce.outbox.OutboxEventRepository;
import com.park.ecommerce.product.Product;
import com.park.ecommerce.product.ProductRepository;
import com.park.ecommerce.product.ProductService;
import com.park.ecommerce.product.dto.ProductCreateRequest;
import com.park.ecommerce.product.status.StorageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.UnexpectedRollbackException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 트러블슈팅 재현 - 같은 트랜잭션에 참여하는 다른 빈(ProductService)의 예외를 잡고 계속 진행해도,
 * 예외가 난 순간 트랜잭션 전체에 롤백 전용 표시가 붙어 커밋 시점에 전부 롤백된다.
 * 실패 처리로 남긴 품목 기록도 같은 트랜잭션이라 함께 사라진다. 해결한 운영 구현은 InboundIntegrationTest가 검증한다.
 * 테스트 메서드에는 @Transactional을 붙이지 않는다 - 붙이면 테스트가 트랜잭션을 감싸 결과가 달라진다.
 */
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
@Import(NaiveInboundService.class)
@SpringBootTest(properties = {
        "outbox.relay.poll-delay=1h",
        "stock.reservation.expire-poll-delay=1h",
        "logging.level.org.springframework.orm.jpa=DEBUG" // 롤백 전용 표시 로그를 확인
})
class InboundRollbackReproTest {
    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @Autowired
    private NaiveInboundService naiveInboundService;

    @Autowired
    private InboundRepository inboundRepository;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    @DisplayName("미등록 상품의 실패를 잡고 넘어가도 커밋 시점에 UnexpectedRollbackException이 나고 정상 품목과 실패 기록까지 모두 롤백된다")
    void rollsBackEverythingEvenThoughFailureWasCaught(CapturedOutput output) {
        Long a = registerProduct("SKU-R-001");
        Long c = registerProduct("SKU-R-003");
        InboundRequest request = new InboundRequest("ASN-R-001", "SUP-001", List.of(
                new InboundRequest.Line("SKU-R-001", 200),
                new InboundRequest.Line("SKU-R-002", 50), // 등록되지 않은 상품
                new InboundRequest.Line("SKU-R-003", 30)
        ));

        assertThatThrownBy(() -> naiveInboundService.receive(request))
                .isInstanceOf(UnexpectedRollbackException.class);

        assertThat(quantityOf(a)).isZero(); // 정상 품목의 재고 반영도 취소됨
        assertThat(quantityOf(c)).isZero();
        assertThat(inboundRepository.findAll()).isEmpty(); // 예정서와 실패로 기록한 품목이 함께 사라짐
        assertThat(outboxEventRepository.findAll()).isEmpty(); // 색인 이벤트도 취소됨
        assertThat(output.getAll())
                .contains("Participating transaction failed - marking existing transaction as rollback-only")
                .contains("Initiating transaction commit");
    }

    private Long registerProduct(String productCode) {
        return productService.register(new ProductCreateRequest(
                productCode, "재현 테스트 상품", null, null, StorageType.ROOM_TEMPERATURE, 1_000, null, subCategoryId()
        )).productId();
    }

    private int quantityOf(Long productId) {
        return productRepository.findById(productId).map(Product::getStockQuantity).orElseThrow();
    }

    private Long subCategoryId() {
        Category parent = categoryRepository.save(Category.builder().name("테스트 상위 카테고리").build());
        return categoryRepository.save(Category.builder().name("테스트 하위 카테고리").parentId(parent.getId()).build()).getId();
    }
}
