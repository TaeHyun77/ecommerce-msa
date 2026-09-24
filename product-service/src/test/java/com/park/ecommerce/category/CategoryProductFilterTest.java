package com.park.ecommerce.category;

import com.park.ecommerce.product.Product;
import com.park.ecommerce.product.ProductRepository;
import com.park.ecommerce.product.dto.ProductCreateRequest;
import com.park.ecommerce.product.status.ProductStatus;
import com.park.ecommerce.product.status.StorageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 상위 카테고리 필터가 하위 카테고리 상품까지 가져오는지는 서브쿼리가 실제로 실행돼야 확인되므로 MySQL 컨테이너로 검증
@Testcontainers
@SpringBootTest(properties = { // 테스트 중 스케줄러 개입 방지
        "inbound.interface.poll-delay=1h",
        "stock.reservation.expire-poll-delay=1h"
})
class CategoryProductFilterTest {
    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Test
    @DisplayName("상위 카테고리로 조회하면 하위 카테고리 상품을 모두 가져오고 다른 상위 카테고리 상품은 제외한다")
    void findsProductsOfSubCategoriesByTopLevel() {
        Long seafood = category("해산물", null);
        Long octopus = category("쭈꾸미·낙지·오징어", seafood);
        Long clam = category("바지락·대합·키조개", seafood);
        Long vegetable = category("채소", null);
        Long octopusProduct = onSaleProduct(octopus);
        Long clamProduct = onSaleProduct(clam);
        onSaleProduct(category("양파·마늘", vegetable));

        assertThat(productIdsOf(seafood)).containsExactlyInAnyOrder(octopusProduct, clamProduct);
        assertThat(productIdsOf(octopus)).containsExactly(octopusProduct);
    }

    @Test
    @DisplayName("하위 카테고리가 없는 상위 카테고리로 조회하면 빈 결과를 돌려준다")
    void findsNothingForEmptyTopLevel() {
        assertThat(productIdsOf(category("빈 카테고리", null))).isEmpty();
    }

    private List<Long> productIdsOf(Long categoryId) {
        return productRepository.findAllByStatusAndCategory(ProductStatus.ON_SALE, categoryId, PageRequest.of(0, 20))
                .map(Product::getId)
                .getContent();
    }

    private Long category(String name, Long parentId) {
        return categoryRepository.save(Category.builder().name(name).parentId(parentId).build()).getId();
    }

    private Long onSaleProduct(Long categoryId) {
        Product product = new ProductCreateRequest(
                "SKU-" + UUID.randomUUID(), "필터 테스트 상품", null, null, StorageType.ROOM_TEMPERATURE, 1_000, null, categoryId
        ).toEntity();
        ReflectionTestUtils.setField(product, "status", ProductStatus.ON_SALE); // 판매 시작은 입고 확정으로만 일어나므로 직접 설정
        return productRepository.save(product).getId();
    }
}
