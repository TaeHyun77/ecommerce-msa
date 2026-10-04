package com.park.ecommerce.product;

import com.park.ecommerce.product.dto.ProductBulkCreateRequest;
import com.park.ecommerce.product.dto.ProductBulkCreateResponse;
import com.park.ecommerce.product.dto.ProductCreateRequest;
import com.park.ecommerce.product.dto.ProductResponse;
import com.park.ecommerce.product.dto.ProductUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 상품 관리 API
@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
public class ProductAdminController {
    private final ProductService productService;
    private final ProductBulkRegistrationService productBulkRegistrationService;

    // 상품 등록
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse register(@Valid @RequestBody ProductCreateRequest request) {
        return productService.register(request);
    }

    // 상품 수정
    // 판매 중인 상품의 정보가 바뀌면 검색 색인도 함께 갱신
    @PutMapping("/{productId}")
    public ProductResponse update(@PathVariable Long productId, @Valid @RequestBody ProductUpdateRequest request) {
        return productService.update(productId, request);
    }

    // 파일로 받은 상품 목록을 한 번에 등록 - 한 건이라도 잘못되면 아무것도 등록하지 않고 행별 오류를 응답
    @PostMapping("/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductBulkCreateResponse registerAll(@Valid @RequestBody ProductBulkCreateRequest request) {
        return productBulkRegistrationService.registerAll(request);
    }
}