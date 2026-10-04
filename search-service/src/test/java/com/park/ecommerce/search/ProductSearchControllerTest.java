package com.park.ecommerce.search;

import com.park.ecommerce.exception.ProductServiceUnavailableException;
import com.park.ecommerce.exception.SearchErrorCode;
import com.park.ecommerce.exception.SearchException;
import com.park.ecommerce.search.dto.ProductSearchPageResponse;
import com.park.ecommerce.search.dto.ProductSearchResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductSearchController.class)
class ProductSearchControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductSearchService productSearchService;

    @Test
    @DisplayName("검색하면 200과 상품 목록과 같은 형식의 결과를 응답한다")
    void respondsSearchResult() throws Exception {
        given(productSearchService.search("고등어", 1, 10)).willReturn(new ProductSearchPageResponse(
                List.of(new ProductSearchResponse(1L, "노르웨이 고등어", "컬리", 14_900, "FROZEN", null, 2L, false)),
                1, 10, 11, 2, false
        ));

        mockMvc.perform(get("/api/search/products")
                        .param("q", "고등어").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].productId").value(1))
                .andExpect(jsonPath("$.content[0].storageType").value("FROZEN"))
                .andExpect(jsonPath("$.content[0].soldOut").value(false))
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("페이지를 생략하면 첫 페이지 20개를 검색한다")
    void searchesWithDefaults() throws Exception {
        given(productSearchService.search("고등어", 0, 20)).willReturn(new ProductSearchPageResponse(List.of(), 0, 20, 0, 0, false));

        mockMvc.perform(get("/api/search/products").param("q", "고등어"))
                .andExpect(status().isOk());

        verify(productSearchService).search("고등어", 0, 20);
    }

    @Test
    @DisplayName("검색어가 없으면 400을 응답하고 검색하지 않는다")
    void respondsBadRequestWhenQueryMissing() throws Exception {
        mockMvc.perform(get("/api/search/products"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verify(productSearchService, never()).search(anyString(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("페이지 크기가 50을 넘거나 페이지 번호가 음수면 400과 검증 메시지를 응답한다")
    void respondsBadRequestWhenPagingOutOfRange() throws Exception {
        mockMvc.perform(get("/api/search/products").param("q", "고등어").param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("페이지 크기는 50 이하여야 합니다."));
        mockMvc.perform(get("/api/search/products").param("q", "고등어").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("페이지 번호는 0 이상이어야 합니다."));
    }

    @Test
    @DisplayName("검색어 규칙 위반은 오류 코드에 정해진 상태와 코드로 응답한다")
    void respondsSearchErrorCode() throws Exception {
        given(productSearchService.search(anyString(), anyInt(), anyInt()))
                .willThrow(new SearchException(SearchErrorCode.INVALID_SEARCH_QUERY));

        mockMvc.perform(get("/api/search/products").param("q", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEARCH_QUERY"));
    }

    @Test
    @DisplayName("product-service 장애면 503을 응답한다")
    void respondsServiceUnavailableWhenProductServiceDown() throws Exception {
        given(productSearchService.search(anyString(), anyInt(), anyInt()))
                .willThrow(new ProductServiceUnavailableException());

        mockMvc.perform(get("/api/search/products").param("q", "고등어"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PRODUCT_SERVICE_UNAVAILABLE"));
    }

    @Test
    @DisplayName("서킷이 열려 호출이 차단되면 장애와 같은 503을 응답한다")
    void respondsServiceUnavailableWhenCircuitOpen() throws Exception {
        given(productSearchService.search(anyString(), anyInt(), anyInt()))
                .willThrow(CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("productService")));

        mockMvc.perform(get("/api/search/products").param("q", "고등어"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PRODUCT_SERVICE_UNAVAILABLE"));
    }
}
