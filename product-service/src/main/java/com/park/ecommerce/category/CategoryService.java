package com.park.ecommerce.category;

import com.park.ecommerce.category.dto.CategoryRequest;
import com.park.ecommerce.category.dto.CategoryResponse;
import com.park.ecommerce.category.dto.CategoryTreeResponse;
import com.park.ecommerce.exception.category.CategoryErrorCode;
import com.park.ecommerce.exception.category.CategoryException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {
    private final CategoryRepository categoryRepository;

    // 카테고리 등록 - 관리자
    @Transactional
    public CategoryResponse register(CategoryRequest request) {
        if (request.parentId() != null) validateParent(request.parentId());

        return CategoryResponse.from(categoryRepository.save(request.toEntity()));
    }

    // 카테고리 수가 적어 한 번에 조회한 뒤 메모리에서 상위별로 묶음
    public List<CategoryTreeResponse> findCategoryTree() {
        // 1. 모든 카테고리 목록 조회
        List<Category> categories = categoryRepository.findAll(Sort.by("id"));

        // 하위 카테고리만 골라, 부모 카테고리 id를 키로 그 부모의 하위 카테고리 목록을 모은 Map
        Map<Long, List<Category>> childrenByParentId = categories.stream()
                .filter(category -> !category.isTopLevel())
                .collect(Collectors.groupingBy(Category::getParentId));

        // 상위 카테고리만 골라서, 앞에서 만든 Map에서 각자의 하위 카테고리 목록을 꺼내 붙인 트리 응답 목록
        return categories.stream()
                .filter(Category::isTopLevel)
                .map(category -> CategoryTreeResponse.of(category, childrenByParentId.getOrDefault(category.getId(), List.of())))
                .toList();
    }

    // 상품이 상위 카테고리에 바로 붙으면 하위 카테고리로 필터링할 때 빠지므로 하위 카테고리에만 연결
    public void validateProductCategory(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryException(CategoryErrorCode.CATEGORY_NOT_FOUND));

        if (category.isTopLevel()) {
            throw new CategoryException(CategoryErrorCode.NOT_SUB_CATEGORY);
        }
    }

    // 상위 카테고리 아래에만 하위 카테고리를 만들 수 있도록 함
    private void validateParent(Long parentId) {
        Category parent = categoryRepository.findById(parentId)
                .orElseThrow(() -> new CategoryException(CategoryErrorCode.CATEGORY_NOT_FOUND));

        if (!parent.isTopLevel()) {
            throw new CategoryException(CategoryErrorCode.INVALID_PARENT_CATEGORY);
        }
    }
}
