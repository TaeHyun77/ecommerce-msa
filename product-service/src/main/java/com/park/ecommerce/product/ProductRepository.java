package com.park.ecommerce.product;

import com.park.ecommerce.product.status.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    boolean existsByProductCode(String productCode);

    List<Product> findAllByProductCodeIn(Collection<String> productCodes);

    Page<Product> findAllByStatus(ProductStatus status, Pageable pageable);

    Page<Product> findAllByStatusAndCategoryId(ProductStatus status, Long categoryId, Pageable pageable);

    Optional<Product> findByIdAndStatus(Long id, ProductStatus status);

    // 조회 후 더해서 저장하면 동시에 들어온 입고 확정이 서로의 증가분을 덮어쓸 수 있어 DB에서 원자적으로 증가
    // 벌크 연산은 감사(Auditing)를 거치지 않으므로 updatedAt을 직접 갱신
    @Modifying
    @Query("update Product p set p.stockQuantity = p.stockQuantity + :quantity, p.updatedAt = current_timestamp where p.id = :productId")
    int increaseStock(Long productId, int quantity);

    // 재고가 충분할 때만 차감 - 조회 후 비교하면 동시에 들어온 선점이 같은 재고를 보고 함께 차감해 음수가 될 수 있음
    @Modifying
    @Query("update Product p set p.stockQuantity = p.stockQuantity - :quantity, p.updatedAt = current_timestamp where p.id = :productId and p.stockQuantity >= :quantity")
    int decreaseStock(Long productId, int quantity);
}
