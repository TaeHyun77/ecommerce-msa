package com.park.ecommerce.product;

import com.park.ecommerce.product.dto.ProductSummaryResponse;
import com.park.ecommerce.product.status.ProductStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductInternalController.class)
class ProductInternalControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    // 메인 클래스의 @EnableJpaAuditing이 웹 슬라이스 테스트에서도 JPA 메타모델을 요구하므로 대체
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("상품 식별자 목록으로 조회하면 상품 정보와 재고 수량을 응답한다")
    void respondsSummaries() throws Exception {
        given(productService.findSummaries(List.of(1L, 2L))).willReturn(List.of(new ProductSummaryResponse(
                1L, "유기농 우유 900ml", 3_900, null, ProductStatus.ON_SALE, 7
        )));

        mockMvc.perform(get("/internal/products").param("ids", "1", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productId").value(1))
                .andExpect(jsonPath("$[0].price").value(3900))
                .andExpect(jsonPath("$[0].availableQuantity").value(7));
    }

    @Test
    @DisplayName("조회할 상품 식별자가 없으면 400을 응답한다")
    void respondsBadRequestWhenIdsMissing() throws Exception {
        mockMvc.perform(get("/internal/products"))
                .andExpect(status().isBadRequest());
    }
}
