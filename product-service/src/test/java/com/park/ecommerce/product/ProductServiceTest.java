package com.park.ecommerce.product;

import com.park.ecommerce.category.Category;
import com.park.ecommerce.category.CategoryService;
import com.park.ecommerce.exception.category.CategoryErrorCode;
import com.park.ecommerce.exception.category.CategoryException;
import com.park.ecommerce.exception.product.ProductErrorCode;
import com.park.ecommerce.exception.product.ProductException;
import com.park.ecommerce.outbox.OutboxEvent;
import com.park.ecommerce.outbox.OutboxEventRepository;
import com.park.ecommerce.product.dto.ProductCreateRequest;
import com.park.ecommerce.product.dto.ProductDetailResponse;
import com.park.ecommerce.product.dto.ProductPageResponse;
import com.park.ecommerce.product.dto.ProductResponse;
import com.park.ecommerce.product.dto.ProductSummaryResponse;
import com.park.ecommerce.product.dto.ProductUpdateRequest;
import com.park.ecommerce.product.status.ProductStatus;
import com.park.ecommerce.product.status.StorageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryService categoryService;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Spy
    private JsonMapper jsonMapper = JsonMapper.builder().build();

    @InjectMocks
    private ProductService productService;

    @Test
    @DisplayName("상품을 등록하면 판매대기 상태로 저장되고, 판매 전이라 색인 이벤트는 남기지 않는다")
    void registersProduct() {
        given(productRepository.existsByProductCode("SKU-0001")).willReturn(false);
        given(productRepository.save(any(Product.class))).willAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            ReflectionTestUtils.setField(product, "id", 1L); // IDENTITY 전략으로 저장 시 부여되는 식별자를 흉내
            return product;
        });

        ProductResponse response = productService.register(request("SKU-0001"));

        assertThat(response.productId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(ProductStatus.READY);
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 등록된 상품코드면 예외가 발생하고 아무것도 저장하지 않는다")
    void rejectsDuplicateProductCode() {
        given(productRepository.existsByProductCode("SKU-0001")).willReturn(true);

        assertThatThrownBy(() -> productService.register(request("SKU-0001")))
                .isInstanceOf(ProductException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.DUPLICATE_PRODUCT_CODE);

        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("하위 카테고리가 아니면 예외가 발생하고 아무것도 저장하지 않는다")
    void rejectsNonSubCategory() {
        given(productRepository.existsByProductCode("SKU-0001")).willReturn(false);
        given(categoryService.getSubCategory(1L)).willThrow(new CategoryException(CategoryErrorCode.NOT_SUB_CATEGORY));

        assertThatThrownBy(() -> productService.register(request("SKU-0001")))
                .isInstanceOf(CategoryException.class)
                .extracting("errorCode")
                .isEqualTo(CategoryErrorCode.NOT_SUB_CATEGORY);

        verify(productRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("판매중인 상품을 수정하면 변경된 정보로 바꾸고 색인 이벤트를 남긴다")
    void updatesOnSaleProduct() {
        Product product = productWithStatus(1L, ProductStatus.ON_SALE);
        given(productRepository.findWithLockById(1L)).willReturn(Optional.of(product));
        given(categoryService.getSubCategory(2L)).willReturn(subCategory(2L, 10L));

        ProductResponse response = productService.update(1L, updateRequest());

        assertThat(response.name()).isEqualTo("수정된 우유 900ml");
        assertThat(response.categoryId()).isEqualTo(2L);
        OutboxEvent event = savedOutboxEvent();
        assertThat(event.getAggregateId()).isEqualTo(1L);
        assertThat(event.getPayload())
                .contains("수정된 우유 900ml")
                .contains("\"categoryId\":2")
                .contains("\"parentCategoryId\":10")
                .contains("ON_SALE");
    }

    @Test
    @DisplayName("판매중지된 상품을 수정하면 판매중지 상태로 색인 문서를 갱신한다")
    void updatesSuspendedProduct() {
        given(productRepository.findWithLockById(1L)).willReturn(Optional.of(productWithStatus(1L, ProductStatus.SUSPENDED)));
        given(categoryService.getSubCategory(2L)).willReturn(subCategory(2L, 10L));

        productService.update(1L, updateRequest());

        assertThat(savedOutboxEvent().getPayload()).contains("SUSPENDED");
    }

    @Test
    @DisplayName("값이 바뀌지 않아도 색인 이벤트를 남긴다 - 관리자가 수정 요청으로 색인을 다시 맞출 수 있도록")
    void publishesEventEvenWhenNothingChanged() {
        Product product = productWithStatus(1L, ProductStatus.ON_SALE);
        given(productRepository.findWithLockById(1L)).willReturn(Optional.of(product));
        given(categoryService.getSubCategory(1L)).willReturn(subCategory(1L, 10L));
        ProductUpdateRequest sameValues = new ProductUpdateRequest(
                product.getName(), product.getBrand(), product.getDescription(),
                product.getStorageType(), product.getPrice(), product.getThumbnailUrl(), product.getCategoryId()
        );

        productService.update(1L, sameValues);

        assertThat(savedOutboxEvent().getPayload()).contains(product.getName());
    }

    @Test
    @DisplayName("판매를 시작하지 않은 상품을 수정하면 색인 이벤트를 남기지 않는다 - 첫 입고 때 최신 정보로 색인되므로")
    void skipsEventWhenSaleNotStartedOnUpdate() {
        Product product = productWithStatus(1L, ProductStatus.READY);
        given(productRepository.findWithLockById(1L)).willReturn(Optional.of(product));
        given(categoryService.getSubCategory(2L)).willReturn(subCategory(2L, 10L));

        productService.update(1L, updateRequest());

        assertThat(product.getName()).isEqualTo("수정된 우유 900ml");
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("수정할 상품이 없으면 예외가 발생하고 이벤트를 남기지 않는다")
    void rejectsUpdatingUnknownProduct() {
        given(productRepository.findWithLockById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.update(99L, updateRequest()))
                .isInstanceOf(ProductException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND);

        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("하위 카테고리가 아니면 예외가 발생하고 상품 정보를 바꾸지 않는다")
    void rejectsNonSubCategoryOnUpdate() {
        Product product = productWithStatus(1L, ProductStatus.ON_SALE);
        given(productRepository.findWithLockById(1L)).willReturn(Optional.of(product));
        given(categoryService.getSubCategory(2L)).willThrow(new CategoryException(CategoryErrorCode.NOT_SUB_CATEGORY));

        assertThatThrownBy(() -> productService.update(1L, updateRequest()))
                .isInstanceOf(CategoryException.class);

        assertThat(product.getName()).isEqualTo("유기농 우유 900ml");
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("입고로 판매대기 상품의 판매가 시작되면 판매 시작 이벤트를 남긴다")
    void receivesStockAndStartsSale() {
        given(productRepository.increaseStock(1L, 5)).willReturn(1);
        given(productRepository.startSaleIfReady(1L)).willReturn(1);
        given(productRepository.findById(1L)).willReturn(Optional.of(product(1L, 5)));
        given(categoryService.getSubCategory(1L)).willReturn(subCategory(1L, 10L));

        productService.receiveStock(1L, 5);

        OutboxEvent event = savedOutboxEvent();
        assertThat(event.getAggregateId()).isEqualTo(1L);
        assertThat(event.getPayload()).contains("\"parentCategoryId\":10");
    }

    @Test
    @DisplayName("이미 판매를 시작했거나 판매중지된 상품은 입고돼도 이벤트를 남기지 않는다")
    void skipsEventWhenSaleNotStarted() {
        given(productRepository.increaseStock(1L, 5)).willReturn(1);
        given(productRepository.startSaleIfReady(1L)).willReturn(0);

        productService.receiveStock(1L, 5);

        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("입고할 상품이 없으면 예외가 발생하고 판매 상태를 바꾸지 않는다")
    void rejectsReceivingStockOfUnknownProduct() {
        given(productRepository.increaseStock(99L, 5)).willReturn(0);

        assertThatThrownBy(() -> productService.receiveStock(99L, 5))
                .isInstanceOf(IllegalStateException.class);
        verify(productRepository, never()).startSaleIfReady(any());
    }

    @Test
    @DisplayName("재고를 늘린 상품이 있으면 예외 없이 끝난다")
    void increasesStock() {
        given(productRepository.increaseStock(1L, 5)).willReturn(1);

        assertThatCode(() -> productService.increaseStock(1L, 5)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("재고를 늘릴 상품이 없으면 호출한 트랜잭션을 롤백하도록 예외가 발생한다")
    void rejectsIncreasingStockOfUnknownProduct() {
        given(productRepository.increaseStock(99L, 5)).willReturn(0);

        assertThatThrownBy(() -> productService.increaseStock(99L, 5))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("상품 식별자 목록으로 상품 정보와 재고 수량을 함께 조회한다")
    void findsSummariesWithQuantity() {
        given(productRepository.findAllById(List.of(1L))).willReturn(List.of(product(1L, 7)));

        List<ProductSummaryResponse> summaries = productService.findSummaries(List.of(1L));

        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).productId()).isEqualTo(1L);
        assertThat(summaries.get(0).price()).isEqualTo(3_000);
        assertThat(summaries.get(0).availableQuantity()).isEqualTo(7);
    }

    @Test
    @DisplayName("검색 결과를 상품 목록과 같은 형식으로 그릴 수 있도록 브랜드·보관 방법·카테고리를 함께 담는다")
    void findsSummariesWithListFields() {
        given(productRepository.findAllById(List.of(1L))).willReturn(List.of(product(1L, 7)));

        ProductSummaryResponse summary = productService.findSummaries(List.of(1L)).get(0);

        assertThat(summary.brand()).isEqualTo("컬리팜");
        assertThat(summary.storageType()).isEqualTo(StorageType.REFRIGERATED);
        assertThat(summary.categoryId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("등록되지 않은 상품 식별자는 결과에서 빠진다")
    void skipsUnknownProductId() {
        given(productRepository.findAllById(List.of(1L, 99L))).willReturn(List.of(product(1L, 7)));

        List<ProductSummaryResponse> summaries = productService.findSummaries(List.of(1L, 99L));

        assertThat(summaries).extracting(ProductSummaryResponse::productId).containsExactly(1L);
    }

    @Test
    @DisplayName("상품 목록은 판매중 상품만 최신 등록순으로 조회하고 재고가 없으면 품절로 표시한다")
    void findsOnSaleProductsWithSoldOut() {
        PageRequest expected = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id"));
        given(productRepository.findAllByStatus(ProductStatus.ON_SALE, expected))
                .willReturn(new PageImpl<>(List.of(product(2L, 0), product(1L, 5)), expected, 2));

        ProductPageResponse response = productService.findOnSaleProducts(null, 0, 20);

        assertThat(response.content())
                .extracting(item -> item.productId() + ":" + item.soldOut())
                .containsExactly("2:true", "1:false");
        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    @DisplayName("카테고리를 지정하면 해당 카테고리의 판매중 상품만 조회한다")
    void findsOnSaleProductsByCategory() {
        given(productRepository.findAllByStatusAndCategory(eq(ProductStatus.ON_SALE), eq(3L), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));

        productService.findOnSaleProducts(3L, 0, 20);

        verify(productRepository, never()).findAllByStatus(any(), any());
    }

    @Test
    @DisplayName("상품 상세는 재고가 없으면 품절로 표시한다")
    void findsOnSaleProductWithSoldOut() {
        given(productRepository.findByIdAndStatus(1L, ProductStatus.ON_SALE)).willReturn(Optional.of(product(1L, 0)));

        ProductDetailResponse response = productService.findOnSaleProduct(1L);

        assertThat(response.soldOut()).isTrue();
        assertThat(response.description()).isEqualTo("1등급 원유로 만든 유기농 우유");
    }

    @Test
    @DisplayName("판매중이 아니거나 없는 상품의 상세를 조회하면 예외가 발생한다")
    void rejectsNotOnSaleProduct() {
        given(productRepository.findByIdAndStatus(1L, ProductStatus.ON_SALE)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findOnSaleProduct(1L))
                .isInstanceOf(ProductException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND);
    }

    private static Product product(Long id, int stockQuantity) {
        Product product = request("SKU-0001").toEntity();
        ReflectionTestUtils.setField(product, "id", id);
        ReflectionTestUtils.setField(product, "stockQuantity", stockQuantity); // 재고는 원자적 UPDATE 쿼리로만 늘어나므로 필드를 직접 설정
        return product;
    }

    private static Product productWithStatus(Long id, ProductStatus status) {
        Product product = product(id, 5);
        ReflectionTestUtils.setField(product, "status", status); // 상태는 입고 확정 쿼리로만 바뀌므로 필드를 직접 설정
        return product;
    }

    private static ProductUpdateRequest updateRequest() {
        return new ProductUpdateRequest(
                "수정된 우유 900ml", "컬리팜", "수정된 설명",
                StorageType.FROZEN, 4_000, null, 2L
        );
    }

    private OutboxEvent savedOutboxEvent() {
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        return captor.getValue();
    }

    private static Category subCategory(Long id, Long parentId) {
        Category category = Category.builder().name("생선").parentId(parentId).build();
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    private static ProductCreateRequest request(String productCode) {
        return new ProductCreateRequest(
                productCode, "유기농 우유 900ml", "컬리팜", "1등급 원유로 만든 유기농 우유",
                StorageType.REFRIGERATED, 3_000, null, 1L
        );
    }
}
