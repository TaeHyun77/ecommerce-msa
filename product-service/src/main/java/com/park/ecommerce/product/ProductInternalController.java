package com.park.ecommerce.product;

import com.park.ecommerce.product.dto.ProductSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 게이트웨이가 외부로 노출하지 않는 서비스 간 내부 통신 전용 API
@RestController
@RequestMapping("/internal/products")
@RequiredArgsConstructor
public class ProductInternalController {
    private final ProductService productService;

    // 상품 ID 목록을 받아 상품 정보 목록을 반환
    @GetMapping
    public List<ProductSummaryResponse> getProducts(@RequestParam List<Long> ids) {
        return productService.findSummaries(ids);
    }
}
