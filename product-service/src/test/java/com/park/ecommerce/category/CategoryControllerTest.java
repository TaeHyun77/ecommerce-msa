package com.park.ecommerce.category;

import com.park.ecommerce.category.dto.CategoryTreeResponse;
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

@WebMvcTest(CategoryController.class)
class CategoryControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    // 메인 클래스의 @EnableJpaAuditing이 웹 슬라이스 테스트에서도 JPA 메타모델을 요구하므로 대체
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("카테고리 목록을 조회하면 상위 카테고리마다 하위 카테고리를 담아 응답한다")
    void getsCategories() throws Exception {
        given(categoryService.findCategoryTree()).willReturn(List.of(new CategoryTreeResponse(
                1L, "해산물", List.of(new CategoryTreeResponse(2L, "쭈꾸미·낙지·오징어", List.of()))
        )));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("해산물"))
                .andExpect(jsonPath("$[0].children[0].categoryId").value(2))
                .andExpect(jsonPath("$[0].children[0].name").value("쭈꾸미·낙지·오징어"));
    }
}
