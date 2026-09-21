package com.park.ecommerce.product;

import com.park.ecommerce.exception.ProductErrorCode;
import com.park.ecommerce.exception.ProductException;
import com.park.ecommerce.product.dto.ProductDetailResponse;
import com.park.ecommerce.product.dto.ProductListResponse;
import com.park.ecommerce.product.dto.ProductPageResponse;
import com.park.ecommerce.product.status.StorageType;
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

@WebMvcTest(ProductController.class)
class ProductControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    // 메인 클래스의 @EnableJpaAuditing이 웹 슬라이스 테스트에서도 JPA 메타모델을 요구하므로 대체
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("상품 목록을 조회하면 페이지 정보와 함께 품절 여부를 응답한다")
    void getsProducts() throws Exception {
        given(productService.findOnSaleProducts(1L, 0, 20)).willReturn(new ProductPageResponse(
                List.of(new ProductListResponse(1L, "유기농 우유 900ml", "파크팜", 3_900, StorageType.REFRIGERATED, null, 1L, false)),
                0, 20, 1, 1, false
        ));

        mockMvc.perform(get("/api/products").param("categoryId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("유기농 우유 900ml"))
                .andExpect(jsonPath("$.content[0].soldOut").value(false))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("페이지 크기가 상한을 넘으면 400과 검증 메시지를 응답한다")
    void rejectsTooLargeSize() throws Exception {
        mockMvc.perform(get("/api/products").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("페이지 크기는 100 이하여야 합니다."));
    }

    @Test
    @DisplayName("상품 상세를 조회하면 설명을 포함해 응답한다")
    void getsProduct() throws Exception {
        given(productService.findOnSaleProduct(1L)).willReturn(new ProductDetailResponse(
                1L, "SKU-0001", "유기농 우유 900ml", "파크팜", "1등급 원유로 만든 유기농 우유",
                3_900, StorageType.REFRIGERATED, null, 1L, true
        ));

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("1등급 원유로 만든 유기농 우유"))
                .andExpect(jsonPath("$.soldOut").value(true));
    }

    @Test
    @DisplayName("판매중이 아니거나 없는 상품이면 404를 응답한다")
    void respondsNotFound() throws Exception {
        given(productService.findOnSaleProduct(99L)).willThrow(new ProductException(ProductErrorCode.PRODUCT_NOT_FOUND));

        mockMvc.perform(get("/api/products/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }
}
