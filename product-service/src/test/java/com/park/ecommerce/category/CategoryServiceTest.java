package com.park.ecommerce.category;

import com.park.ecommerce.category.dto.CategoryRequest;
import com.park.ecommerce.category.dto.CategoryResponse;
import com.park.ecommerce.category.dto.CategoryTreeResponse;
import com.park.ecommerce.exception.category.CategoryErrorCode;
import com.park.ecommerce.exception.category.CategoryException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {
    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    @DisplayName("상위 카테고리 아래에 하위 카테고리를 등록한다")
    void registersSubCategory() {
        given(categoryRepository.findById(1L)).willReturn(Optional.of(category(1L, "해산물", null)));
        given(categoryRepository.save(any(Category.class))).willAnswer(invocation -> {
            Category category = invocation.getArgument(0);
            ReflectionTestUtils.setField(category, "id", 2L); // IDENTITY 전략으로 저장 시 부여되는 식별자를 흉내
            return category;
        });

        CategoryResponse response = categoryService.register(new CategoryRequest("쭈꾸미·낙지·오징어", 1L));

        assertThat(response.categoryId()).isEqualTo(2L);
        assertThat(response.parentId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("하위 카테고리 아래에 등록하면 예외가 발생하고 아무것도 저장하지 않는다")
    void rejectsThirdLevel() {
        given(categoryRepository.findById(2L)).willReturn(Optional.of(category(2L, "쭈꾸미·낙지·오징어", 1L)));

        assertThatThrownBy(() -> categoryService.register(new CategoryRequest("쭈꾸미", 2L)))
                .isInstanceOf(CategoryException.class)
                .extracting("errorCode")
                .isEqualTo(CategoryErrorCode.INVALID_PARENT_CATEGORY);

        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 상위 카테고리 아래에 등록하면 예외가 발생한다")
    void rejectsUnknownParent() {
        given(categoryRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.register(new CategoryRequest("쭈꾸미·낙지·오징어", 99L)))
                .isInstanceOf(CategoryException.class)
                .extracting("errorCode")
                .isEqualTo(CategoryErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    @DisplayName("카테고리 트리는 상위 카테고리마다 하위 카테고리를 등록순으로 묶는다")
    void findsCategoryTree() {
        given(categoryRepository.findAll(Sort.by("id"))).willReturn(List.of(
                category(1L, "해산물", null),
                category(2L, "쭈꾸미·낙지·오징어", 1L),
                category(3L, "채소", null),
                category(4L, "바지락·대합·키조개", 1L)
        ));

        List<CategoryTreeResponse> tree = categoryService.findCategoryTree();

        assertThat(tree).extracting(CategoryTreeResponse::name).containsExactly("해산물", "채소");
        assertThat(tree.get(0).children()).extracting(CategoryTreeResponse::name)
                .containsExactly("쭈꾸미·낙지·오징어", "바지락·대합·키조개");
        assertThat(tree.get(1).children()).isEmpty();
    }

    @Test
    @DisplayName("하위 카테고리면 상품을 등록할 수 있다")
    void acceptsSubCategoryForProduct() {
        given(categoryRepository.findById(2L)).willReturn(Optional.of(category(2L, "쭈꾸미·낙지·오징어", 1L)));

        assertThatCode(() -> categoryService.validateProductCategory(2L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("상위 카테고리에 상품을 등록하면 예외가 발생한다")
    void rejectsTopLevelCategoryForProduct() {
        given(categoryRepository.findById(1L)).willReturn(Optional.of(category(1L, "해산물", null)));

        assertThatThrownBy(() -> categoryService.validateProductCategory(1L))
                .isInstanceOf(CategoryException.class)
                .extracting("errorCode")
                .isEqualTo(CategoryErrorCode.NOT_SUB_CATEGORY);
    }

    @Test
    @DisplayName("존재하지 않는 카테고리에 상품을 등록하면 예외가 발생한다")
    void rejectsUnknownCategoryForProduct() {
        given(categoryRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.validateProductCategory(99L))
                .isInstanceOf(CategoryException.class)
                .extracting("errorCode")
                .isEqualTo(CategoryErrorCode.CATEGORY_NOT_FOUND);
    }

    private static Category category(Long id, String name, Long parentId) {
        Category category = Category.builder().name(name).parentId(parentId).build();
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }
}
