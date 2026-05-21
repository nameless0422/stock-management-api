package com.stockmanagement.domain.product.service;

import com.stockmanagement.domain.admin.setting.service.SystemSettingService;
import com.stockmanagement.domain.inventory.entity.Inventory;
import com.stockmanagement.domain.inventory.repository.InventoryRepository;
import com.stockmanagement.domain.product.category.dto.CategoryResponse;
import com.stockmanagement.domain.product.category.service.CategoryService;
import com.stockmanagement.domain.product.dto.HomeResponse;
import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.product.entity.ProductStatus;
import com.stockmanagement.domain.product.repository.ProductRepository;
import com.stockmanagement.domain.product.review.repository.ReviewRepository;
import com.stockmanagement.domain.product.review.repository.ReviewStatsProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("HomeService 단위 테스트")
class HomeServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private CategoryService categoryService;

    @Mock
    private SystemSettingService systemSettingService;

    @InjectMocks
    private HomeService homeService;

    @Test
    @DisplayName("홈 화면 데이터 — 신상품, 인기 상품, 카테고리 반환")
    void returnsHomeScreenData() {
        // given
        Product product1 = Product.builder()
                .name("상품A").price(BigDecimal.valueOf(10000)).sku("SKU-A").build();
        Product product2 = Product.builder()
                .name("상품B").price(BigDecimal.valueOf(20000)).sku("SKU-B").build();

        given(productRepository.findByStatus(eq(ProductStatus.ACTIVE), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(product1, product2)));
        given(productRepository.findPopularByStatus(eq(ProductStatus.ACTIVE), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(product2)));

        given(reviewRepository.findReviewStatsByProductIdIn(any())).willReturn(List.of());
        given(inventoryRepository.findAllByProductIdIn(any())).willReturn(List.of());

        CategoryResponse category = CategoryResponse.builder().id(1L).name("전자기기").build();
        given(categoryService.getTree()).willReturn(List.of(category));

        // when
        HomeResponse response = homeService.getHomeScreen();

        // then
        assertThat(response.getNewArrivals()).hasSize(2);
        assertThat(response.getPopularProducts()).hasSize(1);
        assertThat(response.getFeaturedCategories()).hasSize(1);
        assertThat(response.getFeaturedCategories().get(0).getName()).isEqualTo("전자기기");

        verify(productRepository).findByStatus(eq(ProductStatus.ACTIVE), any(Pageable.class));
        verify(productRepository).findPopularByStatus(eq(ProductStatus.ACTIVE), any(Pageable.class));
        verify(categoryService).getTree();
    }

    @Test
    @DisplayName("상품이 없을 때 — 빈 목록 반환")
    void returnsEmptyListsWhenNoProducts() {
        given(productRepository.findByStatus(eq(ProductStatus.ACTIVE), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));
        given(productRepository.findPopularByStatus(eq(ProductStatus.ACTIVE), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));
        given(categoryService.getTree()).willReturn(List.of());

        HomeResponse response = homeService.getHomeScreen();

        assertThat(response.getNewArrivals()).isEmpty();
        assertThat(response.getPopularProducts()).isEmpty();
        assertThat(response.getFeaturedCategories()).isEmpty();
    }
}
