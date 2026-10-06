package com.park.ecommerce.inbound;

import com.park.ecommerce.category.Category;
import com.park.ecommerce.category.CategoryRepository;
import com.park.ecommerce.exception.inbound.InboundErrorCode;
import com.park.ecommerce.exception.inbound.InboundException;
import com.park.ecommerce.inbound.dto.InboundRequest;
import com.park.ecommerce.inbound.dto.InboundResponse;
import com.park.ecommerce.outbox.OutboxEvent;
import com.park.ecommerce.outbox.OutboxEventRepository;
import com.park.ecommerce.product.Product;
import com.park.ecommerce.product.ProductRepository;
import com.park.ecommerce.product.ProductService;
import com.park.ecommerce.product.dto.ProductCreateRequest;
import com.park.ecommerce.product.status.ProductStatus;
import com.park.ecommerce.product.status.StorageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 하나의 트랜잭션에서 예정서 저장·재고 반영·색인 기록이 함께 커밋되는지와 재전송·동시 요청은 실제 DB에서만 의미 있게 검증되므로 MySQL 컨테이너로 확인
@Testcontainers
@SpringBootTest(properties = { // 테스트 중 스케줄러 개입 방지
        "outbox.relay.poll-delay=1h",
        "stock.reservation.expire-poll-delay=1h"
})
class InboundIntegrationTest {
    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @Autowired
    private InboundService inboundService;

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

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("모든 상품이 등록되어 있으면 재고가 반영되고 판매가 시작되며 색인 이벤트가 판매중 상태로 기록된다")
    void receivesAllLines() {
        Long a = registerProduct("SKU-I-001");
        Long b = registerProduct("SKU-I-002");

        InboundResponse response = inboundService.receive(request("ASN-I-001", line("SKU-I-001", 200), line("SKU-I-002", 50)));

        assertThat(response.status()).isEqualTo(InboundStatus.COMPLETED);
        assertThat(response.receivedCount()).isEqualTo(2);
        assertThat(response.failedLines()).isEmpty();
        assertThat(quantityOf(a)).isEqualTo(200);
        assertThat(quantityOf(b)).isEqualTo(50);
        assertThat(statusOf(a)).isEqualTo(ProductStatus.ON_SALE);
        // 같은 트랜잭션에서 상품을 미리 읽으면 판매대기 상태가 색인된다 - 반드시 입고로 바뀐 판매중 상태여야 한다
        assertThat(eventsOf(a)).hasSize(1);
        assertThat(eventsOf(a).get(0).getPayload()).contains("ON_SALE");

        Inbound saved = findInbound("ASN-I-001").orElseThrow();
        assertThat(saved.getLines()).extracting(InboundLine::getStatus).containsOnly(InboundLineStatus.RECEIVED);
        assertThat(saved.getLines()).extracting(InboundLine::getProductId).containsExactlyInAnyOrder(a, b);
    }

    @Test
    @DisplayName("등록되지 않은 상품은 실패로 기록하고 나머지 상품은 정상 반영한다")
    void failsUnregisteredLineAndReceivesOthers() {
        Long a = registerProduct("SKU-I-101");
        Long c = registerProduct("SKU-I-103");

        InboundResponse response = inboundService.receive(
                request("ASN-I-101", line("SKU-I-101", 200), line("SKU-I-102", 50), line("SKU-I-103", 30)));

        assertThat(response.status()).isEqualTo(InboundStatus.INCOMPLETE);
        assertThat(response.receivedCount()).isEqualTo(2);
        assertThat(response.failedLines()).extracting(InboundResponse.FailedLine::productCode).containsExactly("SKU-I-102");
        assertThat(quantityOf(a)).isEqualTo(200);
        assertThat(quantityOf(c)).isEqualTo(30);

        Inbound saved = findInbound("ASN-I-101").orElseThrow();
        InboundLine failed = saved.getLines().stream().filter(l -> l.getProductCode().equals("SKU-I-102")).findFirst().orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(InboundLineStatus.FAILED);
        assertThat(failed.getFailReason()).isEqualTo("등록되지 않은 상품입니다.");
        assertThat(failed.getProductId()).isNull();
    }

    @Test
    @DisplayName("상품을 등록한 뒤 같은 예정서를 다시 보내면 실패했던 품목만 반영하고 이미 반영된 품목은 다시 늘리지 않는다")
    void resendProcessesOnlyFailedLines() {
        Long a = registerProduct("SKU-I-201");
        InboundRequest request = request("ASN-I-201", line("SKU-I-201", 200), line("SKU-I-202", 50));
        inboundService.receive(request);
        Long b = registerProduct("SKU-I-202");

        InboundResponse response = inboundService.receive(request);

        assertThat(response.status()).isEqualTo(InboundStatus.COMPLETED);
        assertThat(response.failedLines()).isEmpty();
        assertThat(quantityOf(a)).isEqualTo(200); // 다시 늘지 않음
        assertThat(quantityOf(b)).isEqualTo(50);
        assertThat(statusOf(b)).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("이미 완료된 예정서를 다시 보내도 재고는 한 번만 반영된다")
    void ignoresResendOfCompletedInbound() {
        Long a = registerProduct("SKU-I-301");
        InboundRequest request = request("ASN-I-301", line("SKU-I-301", 200));

        inboundService.receive(request);
        InboundResponse second = inboundService.receive(request);

        assertThat(second.status()).isEqualTo(InboundStatus.COMPLETED);
        assertThat(quantityOf(a)).isEqualTo(200);
        assertThat(eventsOf(a)).hasSize(1);
    }

    @Test
    @DisplayName("같은 상품이 여러 품목에 있으면 예외가 발생하고 아무것도 저장하지 않는다")
    void rejectsDuplicateProduct() {
        Long a = registerProduct("SKU-I-401");

        assertThatThrownBy(() -> inboundService.receive(request("ASN-I-401", line("SKU-I-401", 10), line("SKU-I-401", 20))))
                .isInstanceOf(InboundException.class)
                .extracting("errorCode")
                .isEqualTo(InboundErrorCode.DUPLICATE_LINE_PRODUCT);
        assertThat(quantityOf(a)).isZero();
        assertThat(findInbound("ASN-I-401")).isEmpty();
    }

    @Test
    @DisplayName("같은 예정서가 동시에 두 번 들어와도 재고는 한 번만 반영된다")
    void receivesOnceForConcurrentSameInbound() throws InterruptedException {
        Long a = registerProduct("SKU-I-501");
        InboundRequest request = request("ASN-I-501", line("SKU-I-501", 200));

        List<Throwable> errors = runConcurrently(() -> inboundService.receive(request), () -> inboundService.receive(request));

        assertThat(quantityOf(a)).isEqualTo(200);
        assertThat(errors.size()).isLessThan(2); // 둘 중 하나는 성공 - 다른 하나는 교착·중복으로 실패할 수 있으나 재전송하면 같은 결과
        assertThat(findInbound("ASN-I-501")).isPresent();
    }

    private Long registerProduct(String productCode) {
        return productService.register(new ProductCreateRequest(
                productCode, "입고 테스트 상품", null, null, StorageType.ROOM_TEMPERATURE, 1_000, null, subCategoryId()
        )).productId();
    }

    private static InboundRequest request(String asnNo, InboundRequest.Line... lines) {
        return new InboundRequest(asnNo, "SUP-001", List.of(lines));
    }

    private static InboundRequest.Line line(String productCode, int quantity) {
        return new InboundRequest.Line(productCode, quantity);
    }

    // 락을 거는 조회라 트랜잭션이 필요하고, 라인은 지연 로딩이라 트랜잭션 안에서 함께 읽어 둔다
    private Optional<Inbound> findInbound(String asnNo) {
        return transactionTemplate.execute(status -> {
            Optional<Inbound> inbound = inboundRepository.findByAsnNoForUpdate(asnNo);
            inbound.ifPresent(e -> e.getLines().size());
            return inbound;
        });
    }

    private int quantityOf(Long productId) {
        return productRepository.findById(productId).map(Product::getStockQuantity).orElseThrow();
    }

    private ProductStatus statusOf(Long productId) {
        return productRepository.findById(productId).map(Product::getStatus).orElseThrow();
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
}
