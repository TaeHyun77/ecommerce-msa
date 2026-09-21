package com.park.ecommerce.product;

import com.park.ecommerce.exception.ProductErrorCode;
import com.park.ecommerce.exception.ProductException;
import com.park.ecommerce.product.dto.ProductResponse;
import com.park.ecommerce.product.status.ProductStatus;
import com.park.ecommerce.product.status.StorageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductAdminController.class)
class ProductAdminControllerTest {
    private static final String VALID_REQUEST = """
            {
              "productCode": "SKU-0001",
              "name": "유기농 우유 900ml",
              "storageType": "REFRIGERATED",
              "price": 3000,
              "categoryId": 1
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    // 메인 클래스의 @EnableJpaAuditing이 웹 슬라이스 테스트에서도 JPA 메타모델을 요구하므로 대체
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("상품 등록에 성공하면 201과 등록된 상품 정보를 응답한다")
    void respondsCreated() throws Exception {
        given(productService.register(any())).willReturn(new ProductResponse(
                1L, "SKU-0001", "유기농 우유 900ml", null, null,
                ProductStatus.ON_SALE, StorageType.REFRIGERATED, 3_000, null, 1L
        ));

        mockMvc.perform(post("/api/admin/products").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").value(1))
                .andExpect(jsonPath("$.status").value("ON_SALE"));
    }

    @Test
    @DisplayName("필수값이 없으면 400과 검증 메시지를 응답한다")
    void respondsBadRequestWhenRequiredFieldMissing() throws Exception {
        String request = """
                {
                  "productCode": "SKU-0001",
                  "storageType": "REFRIGERATED",
                  "price": 3000,
                  "categoryId": 1
                }
                """;

        mockMvc.perform(post("/api/admin/products").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("상품명은 필수입니다."));
    }

    @Test
    @DisplayName("존재하지 않는 보관 방법이면 400을 응답한다")
    void respondsBadRequestWhenStorageTypeUnknown() throws Exception {
        String request = VALID_REQUEST.replace("REFRIGERATED", "UNKNOWN");

        mockMvc.perform(post("/api/admin/products").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("이미 등록된 상품코드면 409를 응답한다")
    void respondsConflictWhenDuplicate() throws Exception {
        given(productService.register(any())).willThrow(new ProductException(ProductErrorCode.DUPLICATE_PRODUCT_CODE));

        mockMvc.perform(post("/api/admin/products").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_PRODUCT_CODE"));
    }
}
