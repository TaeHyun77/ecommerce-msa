package com.park.ecommerce.search;

import com.park.ecommerce.keyword.SearchSource;
import com.park.ecommerce.search.dto.ProductSearchPageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 검색 API ( 로그인 안해도 됨 - 게이트웨이가 /api/search/**를 선택적 인증으로 열어 둠, 토큰이 있으면 X-Member-Id 전달 )
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class ProductSearchController {
    private static final String MEMBER_ID_HEADER = "X-Member-Id";
    private static final String VISITOR_ID_HEADER = "X-Visitor-Id";

    private final ProductSearchService productSearchService;

    // source는 자동완성 후보를 눌러 검색했으면 SUGGESTION - 상품명 후보 클릭이 검색어로 집계되지 않게 구분
    @GetMapping("/products")
    public ProductSearchPageResponse searchProducts(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
            @Max(value = 50, message = "페이지 크기는 50 이하여야 합니다.") int size,
            @RequestParam(defaultValue = "TYPED") SearchSource source,
            @RequestHeader(value = MEMBER_ID_HEADER, required = false) Long memberId,
            @RequestHeader(value = VISITOR_ID_HEADER, required = false) String visitorId
    ) {
        return productSearchService.search(q, page, size, source, searcherId(memberId, visitorId));
    }

    // 회원은 기기가 달라도 같은 사람으로 세도록 회원 ID를 우선 - 둘 다 없으면 null(집계하지 않음)
    private String searcherId(Long memberId, String visitorId) {
        if (memberId != null) {
            return "m:" + memberId;
        }
        return visitorId == null ? null : "v:" + visitorId;
    }
}
