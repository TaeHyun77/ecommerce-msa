package com.park.ecommerce.product;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 여러 상품을 한 번에 저장하기 위한 JDBC 배치 INSERT
 * 드라이버의 rewriteBatchedStatements 설정이 켜져 있어야 배치 크기만큼의 행을 INSERT 문 하나로 보낼 수 있음
 */
@Repository
@RequiredArgsConstructor
public class ProductJdbcRepository {
    private static final int BATCH_SIZE = 300; // 한 INSERT 문에 담는 행 수 - 요청 건수가 늘어도 문장 크기가 커지지 않도록 제한, 10,000건 측정에서 300 이상부터 시간이 더 줄지 않음

    private final JdbcTemplate jdbcTemplate;

    private static final String INSERT_SQL = """
            insert into product (product_code, name, brand, description, status, storage_type, price,
                                 thumbnail_url, category_id, stock_quantity, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    // 상태/재고 등 기본값은 엔티티 생성자에서 정해진 값을 그대로 쓰고, @CreatedDate는 거치지 않아 생성/수정일시를 직접 채워야 함
    // 저장된 식별자는 엔티티에 채워지지 않음
    // 배치마다 INSERT 문이 나뉘지만 호출하는 쪽의 트랜잭션 하나로 묶여, 중간에 실패하면 전체가 롤백됨
    public void insertAll(List<Product> products) {
        LocalDateTime now = LocalDateTime.now();

        jdbcTemplate.batchUpdate(INSERT_SQL, products, BATCH_SIZE, (ps, product) -> {
            ps.setObject(1, product.getProductCode());
            ps.setObject(2, product.getName());
            ps.setObject(3, product.getBrand());
            ps.setObject(4, product.getDescription());
            ps.setObject(5, product.getStatus().name());
            ps.setObject(6, product.getStorageType().name());
            ps.setObject(7, product.getPrice());
            ps.setObject(8, product.getThumbnailUrl());
            ps.setObject(9, product.getCategoryId());
            ps.setObject(10, product.getStockQuantity());
            ps.setObject(11, now);
            ps.setObject(12, now);
        });
    }
}
