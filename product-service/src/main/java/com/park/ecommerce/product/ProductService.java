package com.park.ecommerce.product;

import com.park.ecommerce.exception.product.ProductErrorCode;
import com.park.ecommerce.exception.product.ProductException;
import com.park.ecommerce.product.dto.ProductCreateRequest;
import com.park.ecommerce.product.dto.ProductDetailResponse;
import com.park.ecommerce.product.dto.ProductListResponse;
import com.park.ecommerce.product.dto.ProductPageResponse;
import com.park.ecommerce.product.dto.ProductResponse;
import com.park.ecommerce.product.dto.ProductSummaryResponse;
import com.park.ecommerce.product.status.ProductStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {
    private final ProductRepository productRepository;

    @Transactional
    public ProductResponse register(ProductCreateRequest request) {
        if (productRepository.existsByProductCode(request.productCode())) {
            throw new ProductException(ProductErrorCode.DUPLICATE_PRODUCT_CODE);
        }

        return ProductResponse.from(productRepository.save(request.toEntity()));
    }

    // 입고 확정으로 재고가 생기면 판매를 시작한다 - 선점 해제로 재고를 복구할 때는 판매 상태를 바꾸지 않도록 increaseStock과 분리
    @Transactional
    public void receiveStock(Long productId, int quantity) {
        increaseStock(productId, quantity);
        productRepository.startSaleIfReady(productId);
    }

    @Transactional
    public void increaseStock(Long productId, int quantity) {
        int updated = productRepository.increaseStock(productId, quantity);
        // 입고 예정과 재고 선점은 등록된 상품으로만 만들어지므로 갱신 0건은 데이터 불일치 - 호출한 트랜잭션 전체를 롤백한다
        if (updated == 0) {
            throw new IllegalStateException("존재하지 않는 상품입니다. productId=" + productId);
        }
    }

    // 하나라도 차감하지 못하면 예외로 호출한 트랜잭션 전체를 롤백한다
    // 상품 id 오름차순으로 행을 잠가, 여러 상품을 동시에 차감·복구하는 트랜잭션끼리 교착되지 않도록 한다
    @Transactional
    public void decreaseStocks(Map<Long, Integer> quantities) {
        quantities.keySet().stream().sorted().forEach(productId -> {
            if (productRepository.decreaseStock(productId, quantities.get(productId)) == 0) {
                throw new ProductException(productRepository.existsById(productId)
                        ? ProductErrorCode.INSUFFICIENT_STOCK
                        : ProductErrorCode.PRODUCT_NOT_FOUND);
            }
        });
    }

    // decreaseStocks와 같은 순서(상품 id 오름차순)로 행을 잠가 차감과 복구가 서로 교착되지 않도록 한다
    @Transactional
    public void increaseStocks(Map<Long, Integer> quantities) {
        quantities.keySet().stream().sorted().forEach(productId -> increaseStock(productId, quantities.get(productId)));
    }

    // 판매중 상품만 최신 등록순으로 노출 - 판매중지 상품은 숨기고, 품절은 soldOut으로 표시
    // 정렬은 클라이언트가 임의 필드를 지정하지 못하도록 고정
    public ProductPageResponse findOnSaleProducts(Long categoryId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<Product> products = categoryId == null
                ? productRepository.findAllByStatus(ProductStatus.ON_SALE, pageRequest)
                : productRepository.findAllByStatusAndCategoryId(ProductStatus.ON_SALE, categoryId, pageRequest);

        return ProductPageResponse.from(products.map(ProductListResponse::from));
    }

    // 판매중지 상품은 목록과 같은 기준으로 없는 상품처럼 404
    public ProductDetailResponse findOnSaleProduct(Long productId) {
        Product product = productRepository.findByIdAndStatus(productId, ProductStatus.ON_SALE)
                .orElseThrow(() -> new ProductException(ProductErrorCode.PRODUCT_NOT_FOUND));

        return ProductDetailResponse.from(product);
    }

    // 등록되지 않은 상품 식별자는 결과에서 빠진다 - 호출하는 쪽에서 누락 여부로 미등록을 판단
    public List<ProductSummaryResponse> findSummaries(Collection<Long> productIds) {
        return productRepository.findAllById(productIds).stream()
                .map(ProductSummaryResponse::from)
                .toList();
    }

    // 등록되지 않은 상품코드는 결과에서 빠진다 - 호출하는 쪽에서 누락 여부로 미등록을 판단
    public Map<String, Long> findProductIdsByCodes(Collection<String> productCodes) {
        return productRepository.findAllByProductCodeIn(productCodes).stream()
                .collect(Collectors.toMap(Product::getProductCode, Product::getId));
    }
}
