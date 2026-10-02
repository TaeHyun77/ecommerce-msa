package com.park.ecommerce.product;

import com.park.ecommerce.category.CategoryPath;
import com.park.ecommerce.category.CategoryService;
import com.park.ecommerce.exception.ErrorDetail;
import com.park.ecommerce.exception.category.CategoryErrorCode;
import com.park.ecommerce.exception.product.ProductErrorCode;
import com.park.ecommerce.exception.product.ProductException;
import com.park.ecommerce.product.dto.ProductBulkCreateRequest;
import com.park.ecommerce.product.dto.ProductBulkCreateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductBulkRegistrationService {
    private final ProductRepository productRepository;
    private final ProductJdbcRepository productJdbcRepository;
    private final CategoryService categoryService;

    // 원자적 상품 일괄 등록, 실패 시 에러 이유를 담음
    @Transactional
    public ProductBulkCreateResponse registerAll(ProductBulkCreateRequest request) {
        List<ProductBulkCreateRequest.Item> items = request.products();
        Map<CategoryPath, Long> subCategoryIds = categoryService.findSubCategoryIdsByPath();

        List<ErrorDetail> errors = validate(items, subCategoryIds, findRegisteredCodes(items));
        if (!errors.isEmpty()) throw new ProductException(ProductErrorCode.INVALID_INPUT, errors);

        List<Product> products = items.stream()
                .map(item -> item.toEntity(subCategoryIds.get(item.categoryPath())))
                .toList();

        try {
            productJdbcRepository.insertAll(products);
        } catch (DuplicateKeyException e) {
            // 검증 후 저장 전에 다른 요청이 같은 상품코드를 먼저 등록한 경우 - 유니크 제약에 걸려 전체가 롤백
            throw new ProductException(ProductErrorCode.DUPLICATE_PRODUCT_CODE);
        }

        return new ProductBulkCreateResponse(products.size());
    }

    // 상품 코드들 모음
    private Set<String> findRegisteredCodes(List<ProductBulkCreateRequest.Item> items) {
        List<String> productCodes = items.stream().map(ProductBulkCreateRequest.Item::productCode).toList();

        return productRepository.findAllByProductCodeIn(productCodes).stream()
                .map(Product::getProductCode)
                .collect(Collectors.toSet());
    }

    // 모든 행의 오류를 모아서 반환
    private List<ErrorDetail> validate(
            List<ProductBulkCreateRequest.Item> items,
            Map<CategoryPath, Long> subCategoryIds,
            Set<String> registeredCodes
    ) {
        List<ErrorDetail> errors = new ArrayList<>();
        Set<String> requestedCodes = new HashSet<>();

        for (int i = 0; i < items.size(); i++) {
            ProductBulkCreateRequest.Item item = items.get(i);
            String field = "products[" + i + "].";

            // 같은 코드가 여러 번 나오면 처음 나온 행은 그대로 두고 두 번째 행부터 중복으로 표시
            if (!requestedCodes.add(item.productCode())) {
                errors.add(new ErrorDetail(field + "productCode", ProductErrorCode.DUPLICATE_PRODUCT_CODE_IN_REQUEST.getMessage()));
            } else if (registeredCodes.contains(item.productCode())) { // DB에 이미 등록된 코드
                errors.add(new ErrorDetail(field + "productCode", ProductErrorCode.DUPLICATE_PRODUCT_CODE.getMessage()));
            }

            // (상위명, 하위명)이 맞는 하위 카테고리 없음
            if (!subCategoryIds.containsKey(item.categoryPath())) {
                errors.add(new ErrorDetail(field + "categoryName", CategoryErrorCode.CATEGORY_NOT_FOUND.getMessage()));
            }
        }

        return errors;
    }
}
