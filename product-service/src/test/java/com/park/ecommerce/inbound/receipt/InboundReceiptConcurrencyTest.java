package com.park.ecommerce.inbound.receipt;

import com.park.ecommerce.category.Category;
import com.park.ecommerce.category.CategoryRepository;
import com.park.ecommerce.inbound.expectation.InboundExpectation;
import com.park.ecommerce.inbound.expectation.InboundExpectationRepository;
import com.park.ecommerce.exception.inbound.InboundErrorCode;
import com.park.ecommerce.exception.inbound.InboundException;
import com.park.ecommerce.outbox.OutboxEvent;
import com.park.ecommerce.outbox.OutboxEventRepository;
import com.park.ecommerce.product.Product;
import com.park.ecommerce.product.ProductRepository;
import com.park.ecommerce.product.ProductService;
import com.park.ecommerce.product.status.ProductStatus;
import com.park.ecommerce.product.status.StorageType;
import com.park.ecommerce.product.dto.ProductCreateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// 비관적 락과 원자적 재고 증가는 실제 DB에서만 의미 있게 검증되므로 MySQL 컨테이너로 확인
@Testcontainers
@SpringBootTest(properties = { // 테스트 중 스케줄러 개입 방지
        "inbound.interface.poll-delay=1h",
        "outbox.relay.poll-delay=1h"
})
class InboundReceiptConcurrencyTest {
    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @Autowired
    private InboundReceiptService inboundReceiptService;

    @Autowired
    private InboundExpectationRepository inboundExpectationRepository;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    @DisplayName("같은 입고 확정이 동시에 두 번 들어와도 재고는 한 번만 늘어난다")
    void increasesOnceForConcurrentSameReceipt() throws InterruptedException {
        Long productId = registerProductWithExpectation("SKU-C-001", "ASN-C-001");
        InboundReceiptRequest request = receipt("RCV-C-001", "ASN-C-001", "SKU-C-001");

        List<Throwable> errors = runConcurrently(() -> inboundReceiptService.receive(request), () -> inboundReceiptService.receive(request));

        assertThat(errors).isEmpty();
        assertThat(quantityOf(productId)).isEqualTo(195);
    }

    @Test
    @DisplayName("다른 확정 번호가 동시에 들어오면 하나만 반영되고 나머지는 거절된다")
    void acceptsOnlyOneOfConcurrentDifferentReceipts() throws InterruptedException {
        Long productId = registerProductWithExpectation("SKU-C-002", "ASN-C-002");

        List<Throwable> errors = runConcurrently(
                () -> inboundReceiptService.receive(receipt("RCV-C-002", "ASN-C-002", "SKU-C-002")),
                () -> inboundReceiptService.receive(receipt("RCV-C-003", "ASN-C-002", "SKU-C-002"))
        );

        assertThat(errors).singleElement()
                .isInstanceOf(InboundException.class)
                .extracting("errorCode")
                .isEqualTo(InboundErrorCode.ALREADY_RECEIVED);
        assertThat(quantityOf(productId)).isEqualTo(195);
    }

    @Test
    @DisplayName("첫 입고가 확정되면 판매대기 상품이 판매중이 되고, 판매중 상태로 판매 시작 이벤트를 남긴다")
    void startsSaleOnFirstReceipt() {
        Long productId = registerProductWithExpectation("SKU-C-004", "ASN-C-004");

        inboundReceiptService.receive(receipt("RCV-C-004", "ASN-C-004", "SKU-C-004"));

        assertThat(statusOf(productId)).isEqualTo(ProductStatus.ON_SALE);
        // 벌크 UPDATE 이후 다시 읽은 상품으로 스냅샷을 만들었는지 - 이전 값을 읽었다면 READY가 담긴다
        assertThat(eventsOf(productId))
                .singleElement()
                .extracting(OutboxEvent::getPayload)
                .asString()
                .contains("\"status\":\"ON_SALE\"");
    }

    @Test
    @DisplayName("판매중지 상품은 입고가 확정돼도 판매중지를 유지하고 판매 시작 이벤트를 남기지 않는다")
    void keepsSuspendedOnReceipt() {
        Long productId = registerProductWithExpectation("SKU-C-005", "ASN-C-005");
        Product product = productRepository.findById(productId).orElseThrow();
        ReflectionTestUtils.setField(product, "status", ProductStatus.SUSPENDED); // 판매중지 전환 API가 아직 없어 직접 설정
        productRepository.save(product);

        inboundReceiptService.receive(receipt("RCV-C-005", "ASN-C-005", "SKU-C-005"));

        assertThat(statusOf(productId)).isEqualTo(ProductStatus.SUSPENDED);
        assertThat(quantityOf(productId)).isEqualTo(195);
        assertThat(eventsOf(productId)).isEmpty();
    }

    private Long registerProductWithExpectation(String productCode, String asnNo) {
        Long productId = productService.register(new ProductCreateRequest(
                productCode, "동시성 테스트 상품", null, null, StorageType.ROOM_TEMPERATURE, 1_000, null, subCategoryId()
        )).productId();

        InboundExpectation expectation = InboundExpectation.builder()
                .asnNo(asnNo)
                .supplierCode("SUP-001")
                .expectedArrivalAt(LocalDateTime.now())
                .build();
        expectation.addLine(productId, productCode, 200);
        inboundExpectationRepository.save(expectation);
        return productId;
    }

    private static InboundReceiptRequest receipt(String receiptNo, String asnNo, String productCode) {
        return new InboundReceiptRequest(receiptNo, asnNo, List.of(new InboundReceiptRequest.Line(productCode, 195, 5)));
    }

    // 두 작업을 최대한 같은 순간에 시작시키고, 발생한 예외를 모아 돌려준다
    private static List<Throwable> runConcurrently(Runnable first, Runnable second) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (Runnable task : List.of(first, second)) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    task.run();
                } catch (Throwable e) {
                    errors.add(e);
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        return errors;
    }

    private ProductStatus statusOf(Long productId) {
        return productRepository.findById(productId)
                .map(Product::getStatus)
                .orElseThrow();
    }

    private int quantityOf(Long productId) {
        return productRepository.findById(productId)
                .map(Product::getStockQuantity)
                .orElseThrow();
    }

    private List<OutboxEvent> eventsOf(Long productId) {
        return outboxEventRepository.findAll().stream()
                .filter(event -> event.getAggregateId().equals(productId))
                .toList();
    }

    // 상품은 하위 카테고리에만 등록되므로 상위와 하위 카테고리를 함께 만든다
    private Long subCategoryId() {
        Category parent = categoryRepository.save(Category.builder().name("테스트 상위 카테고리").build());
        return categoryRepository.save(Category.builder().name("테스트 하위 카테고리").parentId(parent.getId()).build()).getId();
    }
}
