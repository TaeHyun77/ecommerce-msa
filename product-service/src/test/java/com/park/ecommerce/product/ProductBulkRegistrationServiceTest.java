package com.park.ecommerce.product;

import com.park.ecommerce.category.CategoryPath;
import com.park.ecommerce.category.CategoryService;
import com.park.ecommerce.exception.ErrorDetail;
import com.park.ecommerce.exception.product.ProductErrorCode;
import com.park.ecommerce.exception.product.ProductException;
import com.park.ecommerce.product.dto.ProductBulkCreateRequest;
import com.park.ecommerce.product.dto.ProductBulkCreateResponse;
import com.park.ecommerce.product.status.StorageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductBulkRegistrationServiceTest {
    // 하위명이 같은 카테고리가 상위만 다르게 두 개 있는 실제 데이터 구성
    private static final Map<CategoryPath, Long> SUB_CATEGORY_IDS = Map.of(
            new CategoryPath("수산", "수산가공품"), 3L,
            new CategoryPath("냉장/냉동 식품", "수산가공품"), 4L
    );

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductJdbcRepository productJdbcRepository;

    @Mock
    private CategoryService categoryService;

    @Captor
    private ArgumentCaptor<List<Product>> productsCaptor;

    @InjectMocks
    private ProductBulkRegistrationService productBulkRegistrationService;

    @Test
    @DisplayName("모든 행이 올바르면 상위명과 하위명으로 찾은 카테고리를 붙여 한 번에 저장한다")
    void registersAllProducts() {
        given(categoryService.findSubCategoryIdsByPath()).willReturn(SUB_CATEGORY_IDS);
        given(productRepository.findAllByProductCodeIn(any())).willReturn(List.of());

        ProductBulkCreateResponse response = productBulkRegistrationService.registerAll(request(
                item("SKU-1001", "수산", "수산가공품"),
                item("SKU-1002", "냉장/냉동 식품", "수산가공품")
        ));

        assertThat(response.registeredCount()).isEqualTo(2);
        verify(productJdbcRepository).insertAll(productsCaptor.capture());
        assertThat(productsCaptor.getValue())
                .extracting(Product::getProductCode, Product::getName, Product::getPrice, Product::getCategoryId)
                .containsExactly(
                        tuple("SKU-1001", "상품 SKU-1001", 10_000, 3L),
                        tuple("SKU-1002", "상품 SKU-1002", 10_000, 4L)
                );
    }

    @Test
    @DisplayName("잘못된 행이 있으면 행마다 사유를 모두 담아 예외를 던지고 아무것도 저장하지 않는다")
    void rejectsAllWhenAnyRowInvalid() {
        given(categoryService.findSubCategoryIdsByPath()).willReturn(SUB_CATEGORY_IDS);
        given(productRepository.findAllByProductCodeIn(any())).willReturn(List.of(registeredProduct("SKU-0001")));

        ProductException exception = catchThrowableOfType(ProductException.class, () ->
                productBulkRegistrationService.registerAll(request(
                        item("SKU-1001", "수산", "수산가공품"),
                        item("SKU-1002", "수산", "없는 카테고리"),
                        item("SKU-0001", "수산", "수산가공품"),
                        item("SKU-1001", "수산", "수산가공품")
                )));

        assertThat(exception.getErrorCode()).isEqualTo(ProductErrorCode.INVALID_INPUT);
        assertThat(exception.getErrors()).containsExactly(
                new ErrorDetail("products[1].categoryName", "카테고리를 찾을 수 없습니다."),
                new ErrorDetail("products[2].productCode", "이미 등록된 상품코드입니다."),
                new ErrorDetail("products[3].productCode", "요청 안에 중복된 상품코드가 있습니다.")
        );
        verify(productJdbcRepository, never()).insertAll(any());
    }

    @Test
    @DisplayName("검증한 뒤 저장하기 전에 다른 요청이 같은 상품코드를 먼저 등록하면 중복 예외로 바꿔 던진다")
    void convertsDuplicateKeyOnInsert() {
        given(categoryService.findSubCategoryIdsByPath()).willReturn(SUB_CATEGORY_IDS);
        given(productRepository.findAllByProductCodeIn(any())).willReturn(List.of());
        willThrow(new DuplicateKeyException("Duplicate entry 'SKU-1001'")).given(productJdbcRepository).insertAll(any());

        assertThatThrownBy(() -> productBulkRegistrationService.registerAll(request(item("SKU-1001", "수산", "수산가공품"))))
                .isInstanceOf(ProductException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.DUPLICATE_PRODUCT_CODE);
    }

    private static ProductBulkCreateRequest request(ProductBulkCreateRequest.Item... items) {
        return new ProductBulkCreateRequest(List.of(items));
    }

    private static ProductBulkCreateRequest.Item item(String productCode, String parentCategoryName, String categoryName) {
        return new ProductBulkCreateRequest.Item(
                productCode, "상품 " + productCode, null, null, StorageType.FROZEN, 10_000, null,
                parentCategoryName, categoryName
        );
    }

    private static Product registeredProduct(String productCode) {
        return Product.builder()
                .productCode(productCode)
                .name("상품 " + productCode)
                .storageType(StorageType.FROZEN)
                .price(10_000)
                .categoryId(3L)
                .build();
    }
}
