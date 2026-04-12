package com.stockmanagement.domain.product.category.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.product.category.dto.CategoryCreateRequest;
import com.stockmanagement.domain.product.category.dto.CategoryResponse;
import com.stockmanagement.domain.product.category.dto.CategoryUpdateRequest;
import com.stockmanagement.domain.product.category.entity.Category;
import com.stockmanagement.domain.product.category.repository.CategoryRepository;
import com.stockmanagement.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 카테고리 비즈니스 로직.
 *
 * <p>트리 빌드 전략: 전체 로드 후 in-memory 조립 (카테고리 수가 적으므로 N+1보다 단순 전체 로드가 효율적).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Transactional
    public CategoryResponse create(CategoryCreateRequest request) {
        if (categoryRepository.existsByName(request.getName())) {
            throw new BusinessException(ErrorCode.CATEGORY_DUPLICATE);
        }
        Category parent = resolveParent(request.getParentId());
        Category category = Category.builder()
                .name(request.getName())
                .description(request.getDescription())
                .parent(parent)
                .build();
        return CategoryResponse.from(categoryRepository.save(category));
    }

    /** flat 목록 — 전체 카테고리를 parentId·parentName 포함해 반환 */
    public List<CategoryResponse> getList() {
        return categoryRepository.findAll().stream()
                .map(CategoryResponse::from)
                .toList();
    }

    /**
     * 트리 구조 — 최상위 카테고리를 루트로 children 목록을 채워 반환.
     * 전체 카테고리를 한 번에 로드해 in-memory로 조립한다.
     */
    public List<CategoryResponse> getTree() {
        List<Category> all = categoryRepository.findAll();

        // id → (mutable) children list
        Map<Long, List<CategoryResponse>> childrenMap = new LinkedHashMap<>();
        for (Category c : all) {
            childrenMap.put(c.getId(), new ArrayList<>());
        }

        // 하위 카테고리를 부모 children 리스트에 추가
        List<Category> roots = new ArrayList<>();
        for (Category c : all) {
            if (c.getParent() == null) {
                roots.add(c);
            } else {
                childrenMap.get(c.getParent().getId())
                        .add(CategoryResponse.from(c));
            }
        }

        return roots.stream()
                .map(r -> CategoryResponse.from(r, childrenMap.get(r.getId())))
                .toList();
    }

    /** 단건 조회 — children(하위 카테고리) 목록 포함 */
    public CategoryResponse getById(Long id) {
        Category category = findByIdWithChildren(id);
        List<CategoryResponse> children = category.getChildren().stream()
                .map(CategoryResponse::from)
                .toList();
        return CategoryResponse.from(category, children);
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryUpdateRequest request) {
        Category category = findById(id);

        String newName = request.getName() != null ? request.getName() : category.getName();
        if (!newName.equals(category.getName())
                && categoryRepository.existsByNameAndIdNot(newName, id)) {
            throw new BusinessException(ErrorCode.CATEGORY_DUPLICATE);
        }

        // parentId가 자기 자신이면 순환 참조 방지
        if (request.getParentId() != null && request.getParentId().equals(id)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "자기 자신을 부모로 설정할 수 없습니다.");
        }

        Category parent = resolveParent(request.getParentId());
        category.update(newName, request.getDescription(), parent);
        return CategoryResponse.from(category);
    }

    @Transactional
    public void delete(Long id) {
        Category category = findByIdWithChildren(id);

        if (!category.getChildren().isEmpty()) {
            throw new BusinessException(ErrorCode.CATEGORY_HAS_CHILDREN);
        }
        if (productRepository.existsByCategory_Id(id)) {
            throw new BusinessException(ErrorCode.CATEGORY_HAS_PRODUCTS);
        }

        categoryRepository.delete(category);
    }

    // ===== 내부 헬퍼 =====

    private Category findById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    /** children 컬렉션을 함께 로딩 — getById/delete 전용 */
    private Category findByIdWithChildren(Long id) {
        return categoryRepository.findByIdWithChildren(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    private Category resolveParent(Long parentId) {
        if (parentId == null) return null;
        return categoryRepository.findById(parentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
    }
}
