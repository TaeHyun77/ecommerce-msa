package com.park.ecommerce.product;

import com.park.ecommerce.product.status.ProductStatus;
import com.park.ecommerce.product.status.StorageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

// 직접 작성한 INSERT의 컬럼 매핑, 배치 실행 방식, 중복 예외 변환은 실제 MySQL에서만 확인할 수 있어 컨테이너로 검증
@Testcontainers
@SpringBootTest(properties = { // 테스트 중 스케줄러 개입 방지
        "inbound.interface.poll-delay=1h",
        "outbox.relay.poll-delay=1h"
})
class ProductJdbcRepositoryTest {
    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @Autowired
    private ProductJdbcRepository productJdbcRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("상품을 판매대기·재고 0으로, 생성·수정일시를 채워 저장한다")
    void insertsProducts() {
        productJdbcRepository.insertAll(List.of(
                product("SKU-J-001", "브랜드", "상품 설명", "https://cdn.example.com/1.jpg", 3L),
                product("SKU-J-002", null, null, null, 4L)
        ));

        List<Product> saved = productRepository.findAllByProductCodeIn(List.of("SKU-J-001", "SKU-J-002"));
        assertThat(saved)
                .extracting(Product::getProductCode, Product::getBrand, Product::getDescription, Product::getThumbnailUrl,
                        Product::getStorageType, Product::getPrice, Product::getCategoryId, Product::getStatus, Product::getStockQuantity)
                .containsExactlyInAnyOrder(
                        tuple("SKU-J-001", "브랜드", "상품 설명", "https://cdn.example.com/1.jpg",
                                StorageType.FROZEN, 12_900, 3L, ProductStatus.READY, 0),
                        tuple("SKU-J-002", null, null, null,
                                StorageType.FROZEN, 12_900, 4L, ProductStatus.READY, 0)
                );
        assertThat(saved).allSatisfy(product -> {
            assertThat(product.getCreatedAt()).isNotNull();
            assertThat(product.getUpdatedAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("여러 상품을 INSERT 문 하나로 묶어 저장한다")
    void insertsInSingleStatement() {
        long before = executedInsertStatements();

        productJdbcRepository.insertAll(List.of(
                product("SKU-J-101", null, null, null, 3L),
                product("SKU-J-102", null, null, null, 3L),
                product("SKU-J-103", null, null, null, 3L)
        ));

        assertThat(executedInsertStatements() - before).isEqualTo(1);
    }

    @Test
    @DisplayName("배치 크기(300건)를 넘으면 300건씩 나눠 INSERT 문을 실행한다")
    void splitsInsertsByBatchSize() {
        List<Product> products = IntStream.rangeClosed(1, 650)
                .mapToObj(i -> product("SKU-J-3" + i, null, null, null, 3L))
                .toList();
        long before = executedInsertStatements();

        productJdbcRepository.insertAll(products);

        assertThat(executedInsertStatements() - before).isEqualTo(3); // 300 + 300 + 50
        assertThat(productRepository.count()).isGreaterThanOrEqualTo(650);
    }

    @Test
    @DisplayName("이미 등록된 상품코드가 섞여 있으면 DuplicateKeyException이 발생한다")
    void throwsDuplicateKeyForRegisteredCode() {
        productJdbcRepository.insertAll(List.of(product("SKU-J-201", null, null, null, 3L)));

        assertThatThrownBy(() -> productJdbcRepository.insertAll(List.of(
                product("SKU-J-202", null, null, null, 3L),
                product("SKU-J-201", null, null, null, 3L)
        ))).isInstanceOf(DuplicateKeyException.class);
    }

    // 서버가 실행한 INSERT 문 수 - 여러 행을 넣는 INSERT 문도 1로 센다
    private long executedInsertStatements() {
        return jdbcTemplate.queryForObject("show global status like 'Com_insert'", (rs, rowNum) -> rs.getLong("Value"));
    }

    private static Product product(String productCode, String brand, String description, String thumbnailUrl, Long categoryId) {
        return Product.builder()
                .productCode(productCode)
                .name("상품 " + productCode)
                .brand(brand)
                .description(description)
                .storageType(StorageType.FROZEN)
                .price(12_900)
                .thumbnailUrl(thumbnailUrl)
                .categoryId(categoryId)
                .build();
    }
}
