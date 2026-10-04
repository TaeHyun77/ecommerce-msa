package com.park.ecommerce.search;

import com.park.ecommerce.search.dto.ProductSearchPageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 검색 API ( 로그인 안해도 됨 - 게이트웨이에서 /api/search/**를 인증 없이 열어 둠 )
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class ProductSearchController {
    private final ProductSearchService productSearchService;

    @GetMapping("/products")
    public ProductSearchPageResponse searchProducts(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
            @Max(value = 50, message = "페이지 크기는 50 이하여야 합니다.") int size
    ) {
        return productSearchService.search(q, page, size);
    }
}
