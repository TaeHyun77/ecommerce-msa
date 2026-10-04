package com.park.ecommerce.search;

import com.park.ecommerce.exception.SearchErrorCode;
import com.park.ecommerce.exception.SearchException;
import com.park.ecommerce.search.suggestion.ProductSuggestionController;
import com.park.ecommerce.search.suggestion.ProductSuggestionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductSuggestionController.class)
class ProductSuggestionControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductSuggestionService productSuggestionService;

    @Test
    @DisplayName("입력하면 200과 상품명 문자열 목록을 응답한다")
    void respondsSuggestions() throws Exception {
        given(productSuggestionService.suggest("고등")).willReturn(List.of("고등어 구이", "고등어 조림"));

        mockMvc.perform(get("/api/search/suggestions").param("q", "고등"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("고등어 구이"))
                .andExpect(jsonPath("$[1]").value("고등어 조림"));
    }

    @Test
    @DisplayName("입력이 없으면 400을 응답하고 조회하지 않는다")
    void respondsBadRequestWhenQueryMissing() throws Exception {
        mockMvc.perform(get("/api/search/suggestions"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verify(productSuggestionService, never()).suggest(anyString());
    }

    @Test
    @DisplayName("입력 규칙 위반은 오류 코드에 정해진 상태와 코드로 응답한다")
    void respondsSearchErrorCode() throws Exception {
        given(productSuggestionService.suggest(anyString()))
                .willThrow(new SearchException(SearchErrorCode.INVALID_SEARCH_QUERY));

        mockMvc.perform(get("/api/search/suggestions").param("q", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEARCH_QUERY"));
    }
}
