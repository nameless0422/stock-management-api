package com.stockmanagement.domain.product.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.inventory.repository.InventoryRepository;
import com.stockmanagement.domain.product.category.repository.CategoryRepository;
import com.stockmanagement.domain.product.dto.ProductCreateRequest;
import com.stockmanagement.domain.product.dto.ProductResponse;
import com.stockmanagement.domain.product.dto.ProductUpdateRequest;
import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.product.entity.ProductStatus;
import com.stockmanagement.domain.product.image.repository.ProductImageRepository;
import com.stockmanagement.domain.product.repository.ProductRepository;
import com.stockmanagement.domain.product.review.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService 단위 테스트")
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductSearchService productSearchService;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ProductImageRepository productImageRepository;

    @InjectMocks
    private ProductService productService;

    /** 공통 픽스처 — 저장 완료 상태를 가정한 Product (카테고리 없음) */
    private Product product;

    @BeforeEach
    void setUp() {
        product = Product.builder()
                .name("테스트 상품")
                .description("상품 설명")
                .price(new BigDecimal("10000"))
                .sku("SKU-001")
                .build();
    }

    // ===== create() =====

    @Nested
    @DisplayName("create()")
    class Create {

        private ProductCreateRequest request;

        @BeforeEach
        void setUp() {
            // ProductCreateRequest는 공개 생성자가 없으므로 Mock으로 생성
            // lenient: SKU 중복 예외 경로에서는 name/description/price/categoryId getter가 호출되지 않음
            request = mock(ProductCreateRequest.class);
            lenient().when(request.getName()).thenReturn("테스트 상품");
            lenient().when(request.getDescription()).thenReturn("상품 설명");
            lenient().when(request.getPrice()).thenReturn(new BigDecimal("10000"));
            lenient().when(request.getSku()).thenReturn("SKU-001");
            lenient().when(request.getCategoryId()).thenReturn(null); // 미분류
        }

        @Test
        @DisplayName("신규 SKU이면 상품을 저장하고 응답을 반환한다")
        void savesProductAndReturnsResponse() {
            given(productRepository.existsBySku("SKU-001")).willReturn(false);
            given(productRepository.save(any(Product.class))).willReturn(product);

            ProductResponse response = productService.create(request);

            verify(productRepository).save(any(Product.class));
            assertThat(response.getName()).isEqualTo("테스트 상품");
            assertThat(response.getSku()).isEqualTo("SKU-001");
            assertThat(response.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        }

        @Test
        @DisplayName("SKU가 중복이면 DUPLICATE_SKU 예외를 발생시킨다")
        void throwsWhenSkuDuplicated() {
            given(productRepository.existsBySku("SKU-001")).willReturn(true);

            assertThatThrownBy(() -> productService.create(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.DUPLICATE_SKU));

            verify(productRepository, never()).save(any());
        }
    }

    // ===== getById() =====

    @Nested
    @DisplayName("getById()")
    class GetById {

        @Test
        @DisplayName("상품이 존재하면 ProductResponse를 반환한다")
        void returnsProductResponse() {
            given(productRepository.findById(1L)).willReturn(Optional.of(product));

            ProductResponse response = productService.getById(1L);

            assertThat(response.getName()).isEqualTo("테스트 상품");
            assertThat(response.getSku()).isEqualTo("SKU-001");
        }

        @Test
        @DisplayName("상품이 존재하지 않으면 PRODUCT_NOT_FOUND 예외를 발생시킨다")
        void throwsWhenNotFound() {
            given(productRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> productService.getById(99L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
        }
    }

    // ===== getList() =====

    @Nested
    @DisplayName("getList()")
    class GetList {

        @Test
        @DisplayName("ACTIVE 상품 목록을 페이징하여 반환한다")
        void returnsPagedActiveProducts() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Product> page = new PageImpl<>(List.of(product), pageable, 1);
            given(productRepository.findByStatus(ProductStatus.ACTIVE, pageable)).willReturn(page);

            Page<ProductResponse> result = productService.getList(pageable, null);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("테스트 상품");
        }

        @Test
        @DisplayName("ACTIVE 상품이 없으면 빈 페이지를 반환한다")
        void returnsEmptyPageWhenNoProducts() {
            Pageable pageable = PageRequest.of(0, 10);
            given(productRepository.findByStatus(ProductStatus.ACTIVE, pageable))
                    .willReturn(Page.empty());

            Page<ProductResponse> result = productService.getList(pageable, null);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("findByStatus에 항상 ACTIVE 상태를 전달한다")
        void alwaysQueriesWithActiveStatus() {
            Pageable pageable = PageRequest.of(0, 10);
            given(productRepository.findByStatus(ProductStatus.ACTIVE, pageable))
                    .willReturn(Page.empty());

            productService.getList(pageable, null);

            verify(productRepository).findByStatus(ProductStatus.ACTIVE, pageable);
        }
    }

    // ===== update() =====

    @Nested
    @DisplayName("update()")
    class Update {

        private ProductUpdateRequest request;

        @BeforeEach
        void setUp() {
            // ProductUpdateRequest는 공개 생성자가 없으므로 Mock으로 생성
            // lenient: 미존재 예외 경로에서는 name/description/price/categoryId getter가 호출되지 않음
            request = mock(ProductUpdateRequest.class);
            lenient().when(request.getName()).thenReturn("수정된 상품명");
            lenient().when(request.getDescription()).thenReturn("수정된 설명");
            lenient().when(request.getPrice()).thenReturn(new BigDecimal("20000"));
            lenient().when(request.getCategoryId()).thenReturn(null); // 미분류로 변경
        }

        @Test
        @DisplayName("상품이 존재하면 필드를 수정하고 응답을 반환한다")
        void updatesProductFields() {
            given(productRepository.findById(1L)).willReturn(Optional.of(product));

            ProductResponse response = productService.update(1L, request);

            // dirty checking — save() 호출 없이 필드가 변경됨
            assertThat(response.getName()).isEqualTo("수정된 상품명");
            assertThat(response.getDescription()).isEqualTo("수정된 설명");
            assertThat(response.getPrice()).isEqualByComparingTo("20000");
            assertThat(response.getCategory()).isNull(); // categoryId=null → 미분류
            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("SKU는 수정 대상이 아니므로 변경되지 않는다")
        void doesNotChangeSku() {
            given(productRepository.findById(1L)).willReturn(Optional.of(product));

            ProductResponse response = productService.update(1L, request);

            assertThat(response.getSku()).isEqualTo("SKU-001");
        }

        @Test
        @DisplayName("상품이 존재하지 않으면 PRODUCT_NOT_FOUND 예외를 발생시킨다")
        void throwsWhenNotFound() {
            given(productRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> productService.update(99L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
        }
    }

    // ===== delete() =====

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("상품 상태를 DISCONTINUED로 변경한다 (소프트 삭제)")
        void changesStatusToDiscontinued() {
            given(productRepository.findById(1L)).willReturn(Optional.of(product));

            productService.delete(1L);

            assertThat(product.getStatus()).isEqualTo(ProductStatus.DISCONTINUED);
            verify(productRepository, never()).delete(any());
        }

        @Test
        @DisplayName("상품이 존재하지 않으면 PRODUCT_NOT_FOUND 예외를 발생시킨다")
        void throwsWhenNotFound() {
            given(productRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> productService.delete(99L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
        }
    }
}
