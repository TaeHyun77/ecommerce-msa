package com.park.ecommerce.search;

import com.park.ecommerce.exception.SearchErrorCode;
import com.park.ecommerce.exception.SearchException;
import com.park.ecommerce.search.dto.ProductSearchPageResponse;
import com.park.ecommerce.search.dto.ProductSearchResponse;
import com.park.ecommerce.search.dto.ProductSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

// ES로 판매중 상품의 식별자를 관련도순으로 찾고, 가격/재고/썸네일은 product-service의 현재 값으로 채움
@Service
@RequiredArgsConstructor
public class ProductSearchService {
    private static final int MAX_QUERY_LENGTH = 50;
    private static final int MAX_SEARCH_DEPTH = 10_000; // ES 기본 max_result_window - 이보다 깊은 페이지는 from+size 조회가 거부됨

    private final ProductSearchRepository productSearchRepository;
    private final ProductApiClient productApiClient;

    public ProductSearchPageResponse search(String query, int page, int size) {
        String keyword = normalize(query);
        validateDepth(page, size);

        ProductSearchHits hits = productSearchRepository.search(keyword, page, size);
        List<ProductSearchResponse> content = hits.productIds().isEmpty() ? List.of() : toResponses(hits.productIds());

        int totalPages = (int) Math.ceil((double) hits.totalHits() / size);
        return new ProductSearchPageResponse(content, page, size, hits.totalHits(), totalPages, page + 1 < totalPages);
    }

    // 띄어쓰기만 다른 검색어가 같은 결과를 내도록 공백을 정리
    private String normalize(String query) {
        String keyword = query.strip().replaceAll("\\s+", " ");
        if (keyword.isEmpty() || keyword.length() > MAX_QUERY_LENGTH) {
            throw new SearchException(SearchErrorCode.INVALID_SEARCH_QUERY);
        }
        return keyword;
    }

    private void validateDepth(int page, int size) {
        if ((long) (page + 1) * size > MAX_SEARCH_DEPTH) {
            throw new SearchException(SearchErrorCode.PAGE_OUT_OF_RANGE);
        }
    }

    // ES의 관련도 순서를 유지 - 판매중지가 아직 색인에 반영되지 않았거나 삭제된 상품은 product-service 기준으로 뺀다
    private List<ProductSearchResponse> toResponses(List<Long> productIds) {
        Map<Long, ProductSummaryResponse> products = productApiClient.findProducts(productIds).stream()
                .collect(Collectors.toMap(ProductSummaryResponse::productId, Function.identity()));

        return productIds.stream()
                .map(products::get)
                .filter(Objects::nonNull)
                .filter(ProductSummaryResponse::isOnSale)
                .map(ProductSearchResponse::from)
                .toList();
    }
}
