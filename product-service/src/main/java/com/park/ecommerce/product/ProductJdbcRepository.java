package com.park.ecommerce.product;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 여러 상품을 한 번에 저장하기 위한 JDBC 배치 INSERT
 * 드라이버의 rewriteBatchedStatements 설정이 켜져 있어야 여러 행을 INSERT 문 하나로 보낼 수 있음
 */
@Repository
@RequiredArgsConstructor
public class ProductJdbcRepository {
    private final JdbcTemplate jdbcTemplate;

    private static final String INSERT_SQL = """
            insert into product (product_code, name, brand, description, status, storage_type, price,
                                 thumbnail_url, category_id, stock_quantity, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    // 상태/재고 등 기본값은 엔티티 생성자에서 정해진 값을 그대로 쓰고, @CreatedDate는 거치지 않아 생성/수정일시를 직접 채워야 함
    // 저장된 식별자는 엔티티에 채워지지 않음
    public void insertAll(List<Product> products) {
        LocalDateTime now = LocalDateTime.now();

        List<Object[]> rows = products.stream()
                .map(product -> new Object[]{
                        product.getProductCode(), product.getName(), product.getBrand(), product.getDescription(),
                        product.getStatus().name(), product.getStorageType().name(), product.getPrice(),
                        product.getThumbnailUrl(), product.getCategoryId(), product.getStockQuantity(), now, now
                })
                .toList();

        jdbcTemplate.batchUpdate(INSERT_SQL, rows);
    }
}
