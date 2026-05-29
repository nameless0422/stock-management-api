package com.stockmanagement.domain.product.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.admin.setting.service.SystemSettingService;
import com.stockmanagement.domain.inventory.entity.Inventory;
import com.stockmanagement.domain.inventory.entity.StockStatus;
import com.stockmanagement.domain.inventory.repository.InventoryRepository;
import com.stockmanagement.domain.order.repository.OrderRepository;
import com.stockmanagement.domain.product.category.entity.Category;
import com.stockmanagement.domain.product.category.repository.CategoryRepository;
import com.stockmanagement.domain.product.wishlist.repository.WishlistRepository;
import com.stockmanagement.domain.product.dto.ProductCreateRequest;
import com.stockmanagement.domain.product.dto.ProductResponse;
import com.stockmanagement.domain.product.dto.ProductSearchRequest;
import com.stockmanagement.domain.product.dto.ProductStatusRequest;
import com.stockmanagement.domain.product.dto.ProductUpdateRequest;
import com.stockmanagement.domain.product.dto.ProductVariantCreateRequest;
import com.stockmanagement.domain.product.dto.ProductVariantResponse;
import com.stockmanagement.domain.product.dto.ProductVariantUpdateRequest;
import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.product.entity.ProductStatus;
import com.stockmanagement.domain.product.entity.ProductVariant;
import com.stockmanagement.domain.product.repository.ProductRepository;
import com.stockmanagement.domain.product.repository.ProductVariantRepository;
import com.stockmanagement.domain.product.image.dto.ProductImageResponse;
import com.stockmanagement.domain.product.image.repository.ProductImageRepository;
import com.stockmanagement.domain.product.review.repository.ReviewRepository;
import com.stockmanagement.domain.product.review.repository.ReviewStatsProjection;
import com.stockmanagement.common.event.ProductSyncEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 상품 비즈니스 로직 서비스.
 *
 * <p>트랜잭션 전략:
 * <ul>
 *   <li>클래스 레벨: {@code @Transactional(readOnly = true)} — 조회 성능 최적화 기본값
 *   <li>쓰기 메서드: {@code @Transactional} 으로 개별 오버라이드
 * </ul>
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductSearchService productSearchService;
    private final CategoryRepository categoryRepository;
    private final InventoryRepository inventoryRepository;
    private final ReviewRepository reviewRepository;
    private final ProductImageRepository productImageRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final OrderRepository orderRepository;
    private final WishlistRepository wishlistRepository;
    private final SystemSettingService systemSettingService;

    /**
     * 상품을 등록한다.
     * SKU 중복 여부를 먼저 확인해 충돌을 방지한다.
     * 기본 variant("기본")와 Inventory를 자동 생성한다.
     */
    @Transactional
    public ProductResponse create(ProductCreateRequest request) {
        if (variantRepository.existsBySku(request.getSku())) {
            throw new BusinessException(ErrorCode.DUPLICATE_SKU);
        }
        Category category = resolveCategory(request.getCategoryId());
        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .sku(request.getSku())
                .category(category)
                .build();
        Product saved = productRepository.save(product);

        // 기본 variant 자동 생성
        ProductVariant defaultVariant = variantRepository.save(ProductVariant.builder()
                .product(saved)
                .optionName("기본")
                .sku(request.getSku())
                .price(request.getPrice())
                .build());

        // 기본 variant의 Inventory 자동 생성
        inventoryRepository.save(Inventory.builder().variant(defaultVariant).build());

        eventPublisher.publishEvent(new ProductSyncEvent(saved.getId(), false));
        return ProductResponse.from(saved);
    }

    /** 단건 조회 — 캐시 hit 시 Redis에서 반환, miss 시 DB + 재고·리뷰 통계 + 이미지 + variants 조회 후 캐싱 */
    @Cacheable(cacheNames = "products", key = "#id")
    public ProductResponse getById(Long id) {
        return buildProductResponseWithImages(findById(id));
    }

    /**
     * 인증 사용자용 단건 조회 — canReview 포함. 캐시 우회.
     *
     * <p>canReview = CONFIRMED 주문 보유 &amp;&amp; 해당 상품 리뷰 미작성.
     * 비로그인 경우 {@link #getById(Long)}을 사용한다.
     */
    public ProductResponse getByIdForUser(Long id, Long userId) {
        Product product = findById(id);
        int available = sumAvailableByProductId(id);
        StockStatus stockStatus = StockStatus.of(available, systemSettingService.getLowStockThreshold());
        ReviewStatsProjection stats = reviewRepository.findReviewStatsByProductId(id).orElse(null);
        List<ProductImageResponse> images = productImageRepository
                .findByProductIdOrderByDisplayOrderAsc(id).stream()
                .map(ProductImageResponse::from).toList();
        List<ProductVariantResponse> variants = variantRepository.findByProductId(id).stream()
                .map(ProductVariantResponse::from).toList();
        boolean purchased = orderRepository.existsPurchaseByUserIdAndProductId(userId, id);
        boolean reviewed = reviewRepository.existsByProductIdAndUserId(id, userId);
        return ProductResponse.from(product, null, stockStatus,
                stats != null ? stats.getAvgRating() : null,
                stats != null ? stats.getReviewCount() : 0L,
                images, variants, purchased && !reviewed);
    }

    /**
     * 상품 목록을 조회한다.
     *
     * <p>검색 조건({@code q}, {@code minPrice}, {@code maxPrice}, {@code category}, {@code sort})이
     * 하나라도 있으면 Elasticsearch로 검색하고, 없으면 MySQL 페이징 조회를 사용한다.
     * ES 장애 시 MySQL로 fallback하여 서비스 가용성을 유지한다.
     */
    public Page<ProductResponse> getList(Pageable pageable, ProductSearchRequest request, Long userId) {
        if (request != null && request.hasSearchCondition()) {
            try {
                Page<ProductResponse> esResult = productSearchService.search(request, pageable);
                return enrichSearchResult(esResult, userId);
            } catch (Exception e) {
                log.warn("Elasticsearch 검색 실패, MySQL fallback 사용. query={}", request.getQ(), e);
            }
        }
        // ES 미사용 또는 fallback: sort/keyword를 MySQL에서 처리
        Pageable effectivePageable = toSortedPageable(pageable, request);
        String keyword = request != null ? request.getQ() : null;
        Long categoryId = request != null ? request.getCategoryId() : null;
        Page<Product> products;
        if (categoryId != null) {
            Set<Long> categoryIds = new HashSet<>();
            categoryIds.add(categoryId);
            if (request.isIncludeChildren()) {
                categoryIds.addAll(categoryRepository.findChildIdsByParentId(categoryId));
            }
            products = productRepository.findByStatusAndCategoryIdIn(ProductStatus.ACTIVE, categoryIds, effectivePageable);
        } else if (keyword != null && !keyword.isBlank()) {
            products = productRepository.searchByStatus(ProductStatus.ACTIVE, escapeLike(keyword), effectivePageable);
        } else {
            products = productRepository.findByStatus(ProductStatus.ACTIVE, effectivePageable);
        }
        return enrichPage(products, userId);
    }

    /** sort 파라미터를 Pageable의 Sort로 변환 (MySQL fallback용) */
    private Pageable toSortedPageable(Pageable pageable, ProductSearchRequest request) {
        if (request == null || request.getSort() == null || request.getSort().isBlank()) {
            return pageable;
        }
        Sort sort = switch (request.getSort()) {
            case "price_asc"  -> Sort.by(Sort.Direction.ASC,  "price");
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "price");
            case "newest"     -> Sort.by(Sort.Direction.DESC, "createdAt");
            default           -> pageable.getSort();
        };
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    /**
     * 검색어 prefix로 상품명 자동완성 후보를 반환한다.
     * ES 장애 시 빈 리스트를 반환한다.
     */
    public List<String> suggest(String prefix, int size) {
        try {
            return productSearchService.suggest(prefix, size);
        } catch (Exception e) {
            log.warn("검색 자동완성 실패 (ES 오류). prefix={}", prefix, e);
            return List.of();
        }
    }

    /** 전체 상품 페이징 조회 (ACTIVE + DISCONTINUED, 관리자 전용). search가 있으면 상품명/SKU로 필터링 */
    public Page<ProductResponse> getListAll(Pageable pageable, String search) {
        Page<Product> products = (search != null && !search.isBlank())
                ? productRepository.searchAll(search, pageable)
                : productRepository.findAll(pageable);
        return enrichPage(products, null);
    }

    /**
     * 상품 정보를 수정한다.
     * 더티 체킹(dirty checking)으로 별도 save() 호출 없이 UPDATE가 수행된다.
     * 수정 후 캐시를 무효화하여 다음 조회 시 이미지 포함 응답이 재캐싱되도록 한다.
     */
    @Transactional
    @CacheEvict(cacheNames = "products", key = "#id")
    public ProductResponse update(Long id, ProductUpdateRequest request) {
        Product product = findById(id);
        Category category = resolveCategory(request.getCategoryId());
        product.update(request.getName(), request.getDescription(), request.getPrice(), category);
        eventPublisher.publishEvent(new ProductSyncEvent(product.getId(), false));
        return buildProductResponse(product);
    }

    /**
     * 상품을 삭제한다 (소프트 삭제).
     * 실제 DELETE 대신 status를 DISCONTINUED로 변경해 데이터를 보존한다.
     * 삭제 후 캐시에서 제거한다.
     */
    @Transactional
    @CacheEvict(cacheNames = "products", key = "#id")
    public void delete(Long id) {
        Product product = findById(id);
        product.changeStatus(ProductStatus.DISCONTINUED);
        eventPublisher.publishEvent(new ProductSyncEvent(id, true));
    }

    /** 상품 판매 상태를 변경한다 (ACTIVE ↔ DISCONTINUED). */
    @Transactional
    @CacheEvict(cacheNames = "products", key = "#id")
    public ProductResponse changeStatus(Long id, ProductStatusRequest request) {
        Product product = findById(id);
        product.changeStatus(request.getStatus());
        eventPublisher.publishEvent(new ProductSyncEvent(id,
                request.getStatus() != ProductStatus.ACTIVE));
        return buildProductResponse(product);
    }

    // ===== Variant CRUD =====

    /** 상품의 variant 목록을 조회한다. */
    public List<ProductVariantResponse> getVariants(Long productId) {
        findById(productId);
        return variantRepository.findByProductId(productId).stream()
                .map(ProductVariantResponse::from).toList();
    }

    /** 상품에 variant를 추가한다. */
    @Transactional
    @CacheEvict(cacheNames = "products", key = "#productId")
    public ProductVariantResponse addVariant(Long productId, ProductVariantCreateRequest request) {
        Product product = findById(productId);
        if (variantRepository.existsBySku(request.getSku())) {
            throw new BusinessException(ErrorCode.DUPLICATE_SKU);
        }
        ProductVariant variant = variantRepository.save(ProductVariant.builder()
                .product(product)
                .optionName(request.getOptionName())
                .sku(request.getSku())
                .price(request.getPrice())
                .build());
        // 새 variant의 Inventory 자동 생성
        inventoryRepository.save(Inventory.builder().variant(variant).build());
        return ProductVariantResponse.from(variant);
    }

    /** variant 정보를 수정한다. */
    @Transactional
    @CacheEvict(cacheNames = "products", key = "#productId")
    public ProductVariantResponse updateVariant(Long productId, Long variantId,
                                                ProductVariantUpdateRequest request) {
        ProductVariant variant = findVariant(productId, variantId);
        variant.update(request.getOptionName(), request.getPrice());
        if (request.getStatus() != null) {
            variant.changeStatus(request.getStatus());
        }
        return ProductVariantResponse.from(variant);
    }

    /** variant를 비활성화한다 (DISCONTINUED 전환). */
    @Transactional
    @CacheEvict(cacheNames = "products", key = "#productId")
    public void deactivateVariant(Long productId, Long variantId) {
        ProductVariant variant = findVariant(productId, variantId);
        variant.changeStatus(ProductStatus.DISCONTINUED);
    }

    // ===== 내부 헬퍼 =====

    /** LIKE 패턴 와일드카드(!, %, _)를 이스케이프한다. ESCAPE ! 기준. */
    private static String escapeLike(String value) {
        return com.stockmanagement.common.util.SqlUtils.escapeLike(value);
    }

    /** 상품의 전체 variant 가용 재고를 합산한다. */
    private int sumAvailableByProductId(Long productId) {
        return inventoryRepository.findAllByProductId(productId).stream()
                .mapToInt(Inventory::getAvailable).sum();
    }

    /**
     * 단일 상품에 재고·리뷰 통계를 포함한 응답을 생성한다 (이미지 미포함).
     *
     * <p>재고 레코드가 없으면 availableQuantity=0, 리뷰가 없으면 avgRating=null, reviewCount=0.
     */
    private ProductResponse buildProductResponse(Product product) {
        int available = sumAvailableByProductId(product.getId());
        StockStatus stockStatus = StockStatus.of(available, systemSettingService.getLowStockThreshold());
        ReviewStatsProjection stats = reviewRepository
                .findReviewStatsByProductId(product.getId()).orElse(null);
        return ProductResponse.from(product, available, stockStatus,
                stats != null ? stats.getAvgRating() : null,
                stats != null ? stats.getReviewCount() : 0L);
    }

    /**
     * 단일 상품에 재고·리뷰 통계 + 이미지 + variants를 포함한 응답을 생성한다 (상세 조회용).
     */
    private ProductResponse buildProductResponseWithImages(Product product) {
        int available = sumAvailableByProductId(product.getId());
        StockStatus stockStatus = StockStatus.of(available, systemSettingService.getLowStockThreshold());
        ReviewStatsProjection stats = reviewRepository
                .findReviewStatsByProductId(product.getId()).orElse(null);
        List<ProductImageResponse> images = productImageRepository
                .findByProductIdOrderByDisplayOrderAsc(product.getId()).stream()
                .map(ProductImageResponse::from)
                .toList();
        List<ProductVariantResponse> variants = variantRepository.findByProductId(product.getId()).stream()
                .map(ProductVariantResponse::from).toList();
        return ProductResponse.from(product, available, stockStatus,
                stats != null ? stats.getAvgRating() : null,
                stats != null ? stats.getReviewCount() : 0L,
                images, variants);
    }

    /**
     * 상품 페이지에 재고·리뷰 통계를 배치로 보강한다 (N+1 방지).
     * 한 상품에 여러 variant 재고가 있을 수 있으므로 합산한다.
     */
    private Page<ProductResponse> enrichPage(Page<Product> products, Long userId) {
        if (products.isEmpty()) return products.map(ProductResponse::from);

        List<Long> ids = products.stream().map(Product::getId).toList();
        int threshold = systemSettingService.getLowStockThreshold();

        // variant별 재고를 상품 단위로 합산
        Map<Long, Integer> availableMap = inventoryRepository.findAllByProductIdIn(ids).stream()
                .collect(Collectors.groupingBy(
                        i -> i.getVariant().getProduct().getId(),
                        Collectors.summingInt(Inventory::getAvailable)));

        Map<Long, ReviewStatsProjection> statsMap = reviewRepository
                .findReviewStatsByProductIdIn(ids).stream()
                .collect(Collectors.toMap(ReviewStatsProjection::getProductId, s -> s));

        Set<Long> wishlistedIds = (userId != null)
                ? wishlistRepository.findWishlistedProductIds(userId, ids)
                : Set.of();

        return products.map(p -> {
            int available = availableMap.getOrDefault(p.getId(), 0);
            StockStatus stockStatus = StockStatus.of(available, threshold);
            ReviewStatsProjection stats = statsMap.get(p.getId());
            return ProductResponse.from(
                    p,
                    null, stockStatus,
                    stats != null ? stats.getAvgRating() : null,
                    stats != null ? stats.getReviewCount() : 0L,
                    null,
                    null,
                    null,
                    userId != null ? wishlistedIds.contains(p.getId()) : null);
        });
    }

    /**
     * ES 검색 결과(Page&lt;ProductResponse&gt;)에 재고·리뷰·위시리스트 통계를 보강한다.
     * ES에는 동적 데이터(재고/리뷰)가 없으므로 DB 배치 조회로 채운다.
     */
    private Page<ProductResponse> enrichSearchResult(Page<ProductResponse> esPage, Long userId) {
        if (esPage.isEmpty()) return esPage;

        List<Long> ids = esPage.getContent().stream().map(ProductResponse::getId).toList();
        int threshold = systemSettingService.getLowStockThreshold();

        Map<Long, Integer> availableMap = inventoryRepository.findAllByProductIdIn(ids).stream()
                .collect(Collectors.groupingBy(
                        i -> i.getVariant().getProduct().getId(),
                        Collectors.summingInt(Inventory::getAvailable)));

        Map<Long, ReviewStatsProjection> statsMap = reviewRepository
                .findReviewStatsByProductIdIn(ids).stream()
                .collect(Collectors.toMap(ReviewStatsProjection::getProductId, s -> s));

        Set<Long> wishlistedIds = (userId != null)
                ? wishlistRepository.findWishlistedProductIds(userId, ids)
                : Set.of();

        List<ProductResponse> enriched = esPage.getContent().stream().map(r -> {
            int available = availableMap.getOrDefault(r.getId(), 0);
            ReviewStatsProjection stats = statsMap.get(r.getId());
            return ProductResponse.builder()
                    .id(r.getId())
                    .name(r.getName())
                    .description(r.getDescription())
                    .price(r.getPrice())
                    .sku(r.getSku())
                    .categoryId(r.getCategoryId())
                    .category(r.getCategory())
                    .thumbnailUrl(r.getThumbnailUrl())
                    .status(r.getStatus())
                    .createdAt(r.getCreatedAt())
                    .updatedAt(r.getUpdatedAt())
                    .stockStatus(StockStatus.of(available, threshold))
                    .avgRating(stats != null && stats.getAvgRating() != null
                            ? Math.round(stats.getAvgRating() * 10.0) / 10.0 : null)
                    .reviewCount(stats != null ? stats.getReviewCount() : 0L)
                    .wishlisted(userId != null ? wishlistedIds.contains(r.getId()) : null)
                    .build();
        }).toList();

        return new PageImpl<>(enriched, esPage.getPageable(), esPage.getTotalElements());
    }

    /** 공통 조회 헬퍼 — 없으면 PRODUCT_NOT_FOUND 예외 발생 */
    private Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    /** categoryId가 있으면 Category 조회, null이면 null 반환 */
    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) return null;
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    /** variant를 조회하고 해당 상품 소속인지 검증한다. */
    private ProductVariant findVariant(Long productId, Long variantId) {
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VARIANT_NOT_FOUND));
        if (!variant.getProduct().getId().equals(productId)) {
            throw new BusinessException(ErrorCode.VARIANT_NOT_FOUND);
        }
        return variant;
    }

}
