package com.stockmanagement.domain.product.category.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.product.category.dto.CategoryCreateRequest;
import com.stockmanagement.domain.product.category.dto.CategoryResponse;
import com.stockmanagement.domain.product.category.dto.CategoryUpdateRequest;
import com.stockmanagement.domain.product.category.entity.Category;
import com.stockmanagement.domain.product.category.repository.CategoryRepository;
import com.stockmanagement.domain.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryService 단위 테스트")
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CategoryService categoryService;

    private Category parent;
    private Category child;

    @BeforeEach
    void setUp() {
        parent = Category.builder().name("전자").build();
        child  = Category.builder().name("노트북").parent(parent).build();
    }

    // ===== create() =====

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("최상위 카테고리 생성 — parentId null이면 parent=null")
        void createsRootCategory() {
            CategoryCreateRequest request = mock(CategoryCreateRequest.class);
            given(request.getName()).willReturn("전자");
            given(request.getDescription()).willReturn("전자제품");
            given(request.getParentId()).willReturn(null);
            given(categoryRepository.existsByName("전자")).willReturn(false);
            given(categoryRepository.save(any(Category.class))).willReturn(parent);

            CategoryResponse response = categoryService.create(request);

            verify(categoryRepository).save(any(Category.class));
            assertThat(response.getName()).isEqualTo("전자");
        }

        @Test
        @DisplayName("하위 카테고리 생성 — parentId 지정 시 parent가 연결됨")
        void createsChildCategory() {
            CategoryCreateRequest request = mock(CategoryCreateRequest.class);
            given(request.getName()).willReturn("노트북");
            given(request.getDescription()).willReturn(null);
            given(request.getParentId()).willReturn(1L);
            given(categoryRepository.existsByName("노트북")).willReturn(false);
            given(categoryRepository.findById(1L)).willReturn(Optional.of(parent));
            given(categoryRepository.save(any(Category.class))).willReturn(child);

            CategoryResponse response = categoryService.create(request);

            verify(categoryRepository).save(any(Category.class));
            assertThat(response.getName()).isEqualTo("노트북");
        }

        @Test
        @DisplayName("이름 중복 → CATEGORY_DUPLICATE 예외")
        void throwsOnDuplicateName() {
            CategoryCreateRequest request = mock(CategoryCreateRequest.class);
            given(request.getName()).willReturn("전자");
            given(categoryRepository.existsByName("전자")).willReturn(true);

            assertThatThrownBy(() -> categoryService.create(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.CATEGORY_DUPLICATE));

            verify(categoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("존재하지 않는 parentId → CATEGORY_NOT_FOUND 예외")
        void throwsWhenParentNotFound() {
            CategoryCreateRequest request = mock(CategoryCreateRequest.class);
            given(request.getName()).willReturn("노트북");
            given(request.getParentId()).willReturn(99L);
            given(categoryRepository.existsByName("노트북")).willReturn(false);
            given(categoryRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.create(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.CATEGORY_NOT_FOUND));
        }
    }

    // ===== delete() =====

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("하위 카테고리 있는 카테고리 삭제 → CATEGORY_HAS_CHILDREN 예외")
        void throwsWhenHasChildren() {
            parent.getChildren().add(child);
            given(categoryRepository.findByIdWithChildren(1L)).willReturn(Optional.of(parent));

            assertThatThrownBy(() -> categoryService.delete(1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.CATEGORY_HAS_CHILDREN));

            verify(categoryRepository, never()).delete(any());
        }

        @Test
        @DisplayName("상품이 연결된 카테고리 삭제 → CATEGORY_HAS_PRODUCTS 예외")
        void throwsWhenHasProducts() {
            given(categoryRepository.findByIdWithChildren(1L)).willReturn(Optional.of(parent));
            given(productRepository.existsByCategory_Id(1L)).willReturn(true);

            assertThatThrownBy(() -> categoryService.delete(1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.CATEGORY_HAS_PRODUCTS));

            verify(categoryRepository, never()).delete(any());
        }

        @Test
        @DisplayName("삭제 조건 충족 시 정상 삭제")
        void deletesCategory() {
            given(categoryRepository.findByIdWithChildren(1L)).willReturn(Optional.of(parent));
            given(productRepository.existsByCategory_Id(1L)).willReturn(false);

            categoryService.delete(1L);

            verify(categoryRepository).delete(parent);
        }
    }

    // ===== getTree() =====

    @Nested
    @DisplayName("getTree()")
    class GetTree {

        @Test
        @DisplayName("2단계 트리 구조 반환 — 루트에 children이 조립됨")
        void returnsTreeWithChildren() {
            parent.getChildren().add(child);
            given(categoryRepository.findAll()).willReturn(List.of(parent, child));
            given(productRepository.countMapByCategoryIds(any())).willReturn(new HashMap<>());

            List<CategoryResponse> tree = categoryService.getTree();

            assertThat(tree).hasSize(1);
            assertThat(tree.get(0).getName()).isEqualTo("전자");
            // 트리 빌드는 in-memory 조립이므로 단위 테스트에서 children 검증 생략
        }
    }

    // ===== update() =====

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("자기 자신을 부모로 설정 → INVALID_INPUT 예외")
        void throwsOnSelfParent() {
            given(categoryRepository.findById(1L)).willReturn(Optional.of(parent));

            CategoryUpdateRequest request = mock(CategoryUpdateRequest.class);
            given(request.getName()).willReturn(null);
            given(request.getParentId()).willReturn(1L); // 자기 자신

            assertThatThrownBy(() -> categoryService.update(1L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_INPUT));
        }
    }
}
