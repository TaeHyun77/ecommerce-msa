package com.park.ecommerce.search.suggestion;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 로그인 없이 쓰는 검색창 자동완성 API
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class ProductSuggestionController {
    private final ProductSuggestionService productSuggestionService;

    // 후보 이름만 최대 10개 반환
    @GetMapping("/suggestions")
    public List<String> suggest(@RequestParam String q) {
        return productSuggestionService.suggest(q);
    }
}
