package com.park.ecommerce.product;

import com.park.ecommerce.exception.ErrorDetail;
import com.park.ecommerce.exception.product.ProductErrorCode;
import com.park.ecommerce.exception.product.ProductException;
import com.park.ecommerce.product.dto.ProductBulkCreateResponse;
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

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    private static final String UPDATE_REQUEST = """
            {
              "name": "수정된 우유 900ml",
              "storageType": "FROZEN",
              "price": 4000,
              "categoryId": 2
            }
            """;

    private static final String BULK_ITEM = """
            {"productCode": "%s", "name": "유기농 우유 900ml", "storageType": "REFRIGERATED", "price": 3000,
             "parentCategoryName": "유제품", "categoryName": "우유"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private ProductBulkRegistrationService productBulkRegistrationService;

    // 메인 클래스의 @EnableJpaAuditing이 웹 슬라이스 테스트에서도 JPA 메타모델을 요구하므로 대체
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("상품 등록에 성공하면 201과 등록된 상품 정보를 응답한다")
    void respondsCreated() throws Exception {
        given(productService.register(any())).willReturn(new ProductResponse(
                1L, "SKU-0001", "유기농 우유 900ml", null, null,
                ProductStatus.READY, StorageType.REFRIGERATED, 3_000, null, 1L
        ));

        mockMvc.perform(post("/api/admin/products").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").value(1))
                .andExpect(jsonPath("$.status").value("READY"));
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
                .andExpect(jsonPath("$.code").value("DUPLICATE_PRODUCT_CODE"))
                .andExpect(jsonPath("$.errors").doesNotExist()); // 항목별 오류가 없으면 기존 응답 형식 그대로
    }

    @Test
    @DisplayName("상품 수정에 성공하면 200과 수정된 상품 정보를 응답한다")
    void respondsOkWhenUpdated() throws Exception {
        given(productService.update(eq(1L), any())).willReturn(new ProductResponse(
                1L, "SKU-0001", "수정된 우유 900ml", null, null,
                ProductStatus.ON_SALE, StorageType.FROZEN, 4_000, null, 2L
        ));

        mockMvc.perform(put("/api/admin/products/1").contentType(MediaType.APPLICATION_JSON).content(UPDATE_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(1))
                .andExpect(jsonPath("$.name").value("수정된 우유 900ml"))
                .andExpect(jsonPath("$.status").value("ON_SALE"));
    }

    @Test
    @DisplayName("수정할 때 필수값이 없으면 400과 검증 메시지를 응답한다")
    void respondsBadRequestWhenUpdateFieldMissing() throws Exception {
        String request = UPDATE_REQUEST.replace("\"name\": \"수정된 우유 900ml\",", "");

        mockMvc.perform(put("/api/admin/products/1").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("상품명은 필수입니다."));
    }

    @Test
    @DisplayName("수정할 상품이 없으면 404를 응답한다")
    void respondsNotFoundWhenUpdatingUnknownProduct() throws Exception {
        given(productService.update(eq(99L), any())).willThrow(new ProductException(ProductErrorCode.PRODUCT_NOT_FOUND));

        mockMvc.perform(put("/api/admin/products/99").contentType(MediaType.APPLICATION_JSON).content(UPDATE_REQUEST))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("1,000건까지는 일괄 등록에 성공해 201과 등록 건수를 응답한다")
    void respondsCreatedForBulk() throws Exception {
        given(productBulkRegistrationService.registerAll(any())).willReturn(new ProductBulkCreateResponse(1_000));

        mockMvc.perform(post("/api/admin/products/bulk").contentType(MediaType.APPLICATION_JSON).content(bulkRequest(1_000)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registeredCount").value(1_000));
    }

    @Test
    @DisplayName("일괄 등록이 1,000건을 넘으면 400을 응답한다")
    void respondsBadRequestWhenBulkTooLarge() throws Exception {
        mockMvc.perform(post("/api/admin/products/bulk").contentType(MediaType.APPLICATION_JSON).content(bulkRequest(1_001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("products"))
                .andExpect(jsonPath("$.errors[0].message").value("한 번에 최대 1,000건까지 등록할 수 있습니다."));
    }

    @Test
    @DisplayName("일괄 등록에서 형식이 잘못된 행이 있으면 400과 행 위치를 담은 오류를 모두 응답한다")
    void respondsAllFieldErrorsForBulk() throws Exception {
        String request = """
                {"products": [
                  {"productCode": "SKU-0001", "storageType": "REFRIGERATED", "price": 3000,
                   "parentCategoryName": "유제품", "categoryName": "우유"},
                  {"productCode": "SKU-0002", "name": "유기농 우유 900ml", "storageType": "REFRIGERATED", "price": -1,
                   "parentCategoryName": "유제품", "categoryName": "우유"}
                ]}
                """;

        mockMvc.perform(post("/api/admin/products/bulk").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("products[0].name", "products[1].price")));
    }

    @Test
    @DisplayName("일괄 등록에서 카테고리나 상품코드 검증에 실패하면 400과 행별 오류를 응답한다")
    void respondsRowErrorsForBulk() throws Exception {
        given(productBulkRegistrationService.registerAll(any())).willThrow(new ProductException(
                ProductErrorCode.INVALID_INPUT,
                List.of(new ErrorDetail("products[0].categoryName", "카테고리를 찾을 수 없습니다."))
        ));

        mockMvc.perform(post("/api/admin/products/bulk").contentType(MediaType.APPLICATION_JSON).content(bulkRequest(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.errors[0].field").value("products[0].categoryName"))
                .andExpect(jsonPath("$.errors[0].message").value("카테고리를 찾을 수 없습니다."));
    }

    private static String bulkRequest(int count) {
        String items = IntStream.rangeClosed(1, count)
                .mapToObj(i -> BULK_ITEM.formatted("SKU-%04d".formatted(i)))
                .collect(Collectors.joining(","));
        return "{\"products\": [" + items + "]}";
    }
}
