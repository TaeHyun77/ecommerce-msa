package com.park.ecommerce.reservation;

import com.park.ecommerce.exception.product.ProductErrorCode;
import com.park.ecommerce.exception.product.ProductException;
import com.park.ecommerce.product.Product;
import com.park.ecommerce.product.ProductRepository;
import com.park.ecommerce.product.ProductService;
import com.park.ecommerce.product.dto.ProductCreateRequest;
import com.park.ecommerce.product.status.StorageType;
import com.park.ecommerce.reservation.domain.StockReservationRepository;
import com.park.ecommerce.reservation.dto.StockReservationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

// 조건부 UPDATE가 동시 요청에서도 재고를 지키는지는 실제 DB에서만 의미 있게 검증되므로 MySQL 컨테이너로 확인
@Testcontainers
@SpringBootTest(properties = { // 테스트 중 스케줄러 개입 방지
        "inbound.interface.poll-delay=1h",
        "stock.reservation.expire-poll-delay=1h"
})
class StockReservationConcurrencyTest {
    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @Autowired
    private StockReservationService stockReservationService;

    @Autowired
    private StockReservationRepository stockReservationRepository;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Test
    @DisplayName("재고보다 많은 선점이 동시에 들어와도 재고만큼만 성공하고 재고는 음수가 되지 않는다")
    void reservesOnlyAvailableStockConcurrently() throws InterruptedException {
        Long productId = productWithStock(5);

        List<Throwable> errors = runConcurrently(IntStream.range(0, 10)
                .<Runnable>mapToObj(i -> () -> stockReservationService.reserve(request(UUID.randomUUID().toString(), productId)))
                .toList());

        assertThat(errors).hasSize(5)
                .allSatisfy(error -> assertThat(error)
                        .isInstanceOf(ProductException.class)
                        .extracting("errorCode")
                        .isEqualTo(ProductErrorCode.INSUFFICIENT_STOCK));
        assertThat(stockOf(productId)).isZero();
    }

    @Test
    @DisplayName("만료 해제와 명시적 해제가 동시에 실행돼도 재고는 한 번만 복구된다")
    void restoresOnceWhenExpireAndReleaseRace() throws InterruptedException {
        Long productId = productWithStock(10);
        String orderNo = UUID.randomUUID().toString();
        stockReservationService.reserve(new StockReservationRequest(
                orderNo, LocalDateTime.now().minusSeconds(1), List.of(new StockReservationRequest.Item(productId, 3))
        ));
        Long reservationId = stockReservationRepository.findByOrderNo(orderNo).orElseThrow().getId();

        List<Throwable> errors = runConcurrently(List.of(
                () -> stockReservationService.expire(reservationId),
                () -> stockReservationService.release(orderNo)
        ));

        assertThat(errors).isEmpty();
        assertThat(stockOf(productId)).isEqualTo(10);
    }

    private Long productWithStock(int stock) {
        Long productId = productService.register(new ProductCreateRequest(
                "SKU-" + UUID.randomUUID(), "동시성 테스트 상품", null, null, StorageType.ROOM_TEMPERATURE, 1_000, null, 1L
        )).productId();
        productService.increaseStock(productId, stock);
        return productId;
    }

    private int stockOf(Long productId) {
        return productRepository.findById(productId)
                .map(Product::getStockQuantity)
                .orElseThrow();
    }

    private static StockReservationRequest request(String orderNo, Long productId) {
        return new StockReservationRequest(
                orderNo, LocalDateTime.now().plusMinutes(10), List.of(new StockReservationRequest.Item(productId, 1))
        );
    }

    // 작업들을 최대한 같은 순간에 시작시키고, 발생한 예외를 모아 돌려준다
    private static List<Throwable> runConcurrently(List<Runnable> tasks) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(tasks.size());
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (Runnable task : tasks) {
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
