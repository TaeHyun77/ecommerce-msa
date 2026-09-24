package com.park.ecommerce.category;

import com.park.ecommerce.category.dto.CategoryResponse;
import com.park.ecommerce.exception.category.CategoryErrorCode;
import com.park.ecommerce.exception.category.CategoryException;
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

@WebMvcTest(CategoryAdminController.class)
class CategoryAdminControllerTest {
    private static final String VALID_REQUEST = """
            {
              "name": "쭈꾸미·낙지·오징어",
              "parentId": 1
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    // 메인 클래스의 @EnableJpaAuditing이 웹 슬라이스 테스트에서도 JPA 메타모델을 요구하므로 대체
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("카테고리 등록에 성공하면 201과 등록된 카테고리 정보를 응답한다")
    void respondsCreated() throws Exception {
        given(categoryService.register(any())).willReturn(new CategoryResponse(2L, "쭈꾸미·낙지·오징어", 1L));

        mockMvc.perform(post("/api/admin/categories").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryId").value(2))
                .andExpect(jsonPath("$.parentId").value(1));
    }

    @Test
    @DisplayName("카테고리명이 없으면 400과 검증 메시지를 응답한다")
    void respondsBadRequestWhenNameMissing() throws Exception {
        mockMvc.perform(post("/api/admin/categories").contentType(MediaType.APPLICATION_JSON).content("{\"parentId\": 1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("카테고리명은 필수입니다."));
    }

    @Test
    @DisplayName("하위 카테고리 아래에 등록하면 400을 응답한다")
    void respondsBadRequestWhenThirdLevel() throws Exception {
        given(categoryService.register(any())).willThrow(new CategoryException(CategoryErrorCode.INVALID_PARENT_CATEGORY));

        mockMvc.perform(post("/api/admin/categories").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARENT_CATEGORY"));
    }
}
