package com.stockmanagement.domain.product.service;

import com.stockmanagement.domain.inventory.entity.Inventory;
import com.stockmanagement.domain.inventory.repository.InventoryRepository;
import com.stockmanagement.domain.product.category.dto.CategoryResponse;
import com.stockmanagement.domain.product.category.service.CategoryService;
import com.stockmanagement.domain.product.dto.HomeResponse;
import com.stockmanagement.domain.product.dto.ProductResponse;
import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.product.entity.ProductStatus;
import com.stockmanagement.domain.product.repository.ProductRepository;
import com.stockmanagement.domain.product.review.repository.ReviewRepository;
import com.stockmanagement.domain.product.review.repository.ReviewStatsProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 홈 화면 집계 서비스.
 *
 * <p>신상품, 인기 상품, 추천 카테고리를 단일 응답으로 조합한다.
 * Redis 캐시(TTL 5분)로 DB 부하를 최소화한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeService {

    private static final int HOME_PRODUCT_SIZE = 8;

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final ReviewRepository reviewRepository;
    private final CategoryService categoryService;

    @Cacheable(cacheNames = "home", key = "'screen'")
    public HomeResponse getHomeScreen() {
        // 1. 신상품: 최신 ACTIVE 상품 8개
        List<Product> newArrivals = productRepository.findByStatus(
                ProductStatus.ACTIVE,
                PageRequest.of(0, HOME_PRODUCT_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).getContent();

        // 2. 인기 상품: 리뷰 많은 순 ACTIVE 상품 8개
        List<Product> popular = productRepository.findPopularByStatus(
                ProductStatus.ACTIVE,
                PageRequest.of(0, HOME_PRODUCT_SIZE)
        ).getContent();

        // 3. 배치 조회: 두 목록의 상품 ID를 합쳐 review/inventory 한 번에 조회
        List<Long> allProductIds = Stream.of(newArrivals, popular)
                .flatMap(Collection::stream)
                .map(Product::getId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, ReviewStatsProjection> reviewStatsMap = allProductIds.isEmpty()
                ? Map.of()
                : reviewRepository.findReviewStatsByProductIdIn(allProductIds).stream()
                        .collect(Collectors.toMap(ReviewStatsProjection::getProductId, r -> r));

        Map<Long, Integer> inventoryMap = allProductIds.isEmpty()
                ? Map.of()
                : inventoryRepository.findAllByProductIdIn(allProductIds).stream()
                        .collect(Collectors.toMap(
                                inv -> inv.getProduct().getId(),
                                Inventory::getAvailable,
                                (a, b) -> a));

        // 4. DTO 변환
        List<ProductResponse> newArrivalResponses = toProductResponses(newArrivals, reviewStatsMap, inventoryMap);
        List<ProductResponse> popularResponses = toProductResponses(popular, reviewStatsMap, inventoryMap);

        // 5. 추천 카테고리 (기존 캐시된 트리 재사용)
        List<CategoryResponse> categories = categoryService.getTree();

        return HomeResponse.builder()
                .newArrivals(newArrivalResponses)
                .popularProducts(popularResponses)
                .featuredCategories(categories)
                .build();
    }

    private List<ProductResponse> toProductResponses(
            List<Product> products,
            Map<Long, ReviewStatsProjection> reviewStatsMap,
            Map<Long, Integer> inventoryMap) {
        return products.stream().map(p -> {
            ReviewStatsProjection stats = reviewStatsMap.get(p.getId());
            Double avgRating = stats != null ? stats.getAvgRating() : null;
            Long reviewCount = stats != null ? stats.getReviewCount() : null;
            Integer available = inventoryMap.get(p.getId());
            return ProductResponse.from(p, available, avgRating, reviewCount);
        }).collect(Collectors.toList());
    }
}
