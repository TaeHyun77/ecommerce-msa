package com.park.ecommerce.product;

import com.park.ecommerce.product.status.ProductStatus;
import com.park.ecommerce.product.status.StorageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ProductTest {

    @Test
    @DisplayName("등록된 상품은 판매대기 상태로 시작한다")
    void startsReady() {
        Product product = validProduct().build();

        assertThat(product.getStatus()).isEqualTo(ProductStatus.READY);
    }

    @Test
    @DisplayName("등록된 상품은 재고 0개인 품절 상태로 시작한다")
    void startsSoldOutWithNoStock() {
        Product product = validProduct().build();

        assertThat(product.getStockQuantity()).isZero();
        assertThat(product.isSoldOut()).isTrue();
    }

    @Test
    @DisplayName("재고가 1개라도 있으면 품절이 아니다")
    void isNotSoldOutWhenStockRemains() {
        Product product = validProduct().build();
        ReflectionTestUtils.setField(product, "stockQuantity", 1); // 재고는 원자적 UPDATE 쿼리로만 늘어나므로 필드를 직접 설정

        assertThat(product.isSoldOut()).isFalse();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = -1)
    @DisplayName("판매가가 없거나 음수이면 예외가 발생한다")
    void rejectsInvalidPrice(Integer price) {
        assertThatIllegalArgumentException().isThrownBy(() -> validProduct().price(price).build());
    }

    @Test
    @DisplayName("판매가 0원은 허용한다")
    void allowsZeroPrice() {
        assertThatCode(() -> validProduct().price(0).build()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("카테고리가 없으면 예외가 발생한다")
    void rejectsNullCategoryId() {
        assertThatIllegalArgumentException().isThrownBy(() -> validProduct().categoryId(null).build());
    }

    @Test
    @DisplayName("상품 정보를 수정해도 상품코드·판매 상태·재고는 그대로다")
    void updatesInfoAndKeepsCodeStatusAndStock() {
        Product product = validProduct().build();
        ReflectionTestUtils.setField(product, "status", ProductStatus.ON_SALE);
        ReflectionTestUtils.setField(product, "stockQuantity", 5); // 재고는 원자적 UPDATE 쿼리로만 바뀌므로 필드를 직접 설정

        product.update("수정된 우유 900ml", "컬리팜", "설명", StorageType.FROZEN, 4_000, "https://cdn.example.com/1.jpg", 2L);

        assertThat(product.getName()).isEqualTo("수정된 우유 900ml");
        assertThat(product.getBrand()).isEqualTo("컬리팜");
        assertThat(product.getDescription()).isEqualTo("설명");
        assertThat(product.getStorageType()).isEqualTo(StorageType.FROZEN);
        assertThat(product.getPrice()).isEqualTo(4_000);
        assertThat(product.getThumbnailUrl()).isEqualTo("https://cdn.example.com/1.jpg");
        assertThat(product.getCategoryId()).isEqualTo(2L);
        assertThat(product.getProductCode()).isEqualTo("SKU-0001");
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
        assertThat(product.getStockQuantity()).isEqualTo(5);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = -1)
    @DisplayName("수정할 판매가가 없거나 음수이면 예외가 발생한다")
    void rejectsInvalidPriceOnUpdate(Integer price) {
        Product product = validProduct().build();

        assertThatIllegalArgumentException().isThrownBy(
                () -> product.update("수정된 우유", null, null, StorageType.FROZEN, price, null, 2L));
    }

    @Test
    @DisplayName("수정할 카테고리가 없으면 예외가 발생하고 기존 정보를 유지한다")
    void rejectsNullCategoryIdOnUpdate() {
        Product product = validProduct().build();

        assertThatIllegalArgumentException().isThrownBy(
                () -> product.update("수정된 우유", null, null, StorageType.FROZEN, 4_000, null, null));
        assertThat(product.getName()).isEqualTo("유기농 우유 900ml");
    }

    private static Product.ProductBuilder validProduct() {
        return Product.builder()
                .productCode("SKU-0001")
                .name("유기농 우유 900ml")
                .storageType(StorageType.REFRIGERATED)
                .price(3_000)
                .categoryId(1L);
    }
}
