package com.stockmanagement.domain.product.dto;

import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.product.entity.ProductStatus;
import com.stockmanagement.domain.product.image.dto.ProductImageResponse;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 상품 조회 응답 DTO.
 *
 * <p>엔티티를 직접 반환하지 않고 필요한 필드만 추려 노출한다.
 * 엔티티 내부 구조가 바뀌어도 API 스펙이 바뀌지 않도록 분리한다.
 *
 * <p>{@link #from(Product)} 정적 팩토리로 생성한다.
 * {@code @Jacksonized}로 Redis 캐시 역직렬화를 지원한다.
 */
@Getter
@Builder
@Jacksonized
public class ProductResponse {

    private final Long id;
    private final String name;
    private final String description;
    private final BigDecimal price;
    private final String sku;
    /** 카테고리 ID — null이면 미분류 */
    private final Long categoryId;
    /** 카테고리 이름 — ES 검색 호환성 및 UI 표시용 */
    private final String category;
    /** 대표 썸네일 URL — null이면 이미지 미등록 */
    private final String thumbnailUrl;
    private final ProductStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    /** 현재 가용 재고 수량 — null이면 재고 정보 미포함 */
    private final Integer availableQuantity;
    /** 상품 평균 별점 (0.0~5.0) — null이면 리뷰 없음 */
    private final Double avgRating;
    /** 총 리뷰 수 — null이면 리뷰 정보 미포함 */
    private final Long reviewCount;
    /** 상품 이미지 목록 — null이면 이미지 정보 미포함 (상세 조회 시만 포함) */
    private final List<ProductImageResponse> images;

    /**
     * 현재 사용자의 리뷰 작성 가능 여부.
     * null: 비로그인 또는 정보 미포함
     * true: 구매 완료 + 아직 리뷰 미작성
     * false: 미구매 또는 이미 리뷰 작성
     */
    private final Boolean canReview;

    /** 현재 사용자의 위시리스트 등록 여부 — null이면 비로그인 또는 정보 미포함 */
    private final Boolean wishlisted;

    /** Product 엔티티만으로 변환 (재고·리뷰 통계 미포함). */
    public static ProductResponse from(Product product) {
        return from(product, null, null, null, null, null, null);
    }

    /** Product 엔티티 + 재고·리뷰 통계를 포함한 전체 응답으로 변환 (이미지 미포함). */
    public static ProductResponse from(Product product, Integer availableQuantity,
                                       Double avgRating, Long reviewCount) {
        return from(product, availableQuantity, avgRating, reviewCount, null, null, null);
    }

    /** Product 엔티티 + 재고·리뷰 통계 + 이미지 목록을 포함한 전체 응답으로 변환. */
    public static ProductResponse from(Product product, Integer availableQuantity,
                                       Double avgRating, Long reviewCount,
                                       List<ProductImageResponse> images) {
        return from(product, availableQuantity, avgRating, reviewCount, images, null, null);
    }

    /** Product 엔티티 + 재고·리뷰 통계 + 이미지 + canReview를 포함한 전체 응답으로 변환. */
    public static ProductResponse from(Product product, Integer availableQuantity,
                                       Double avgRating, Long reviewCount,
                                       List<ProductImageResponse> images, Boolean canReview) {
        return from(product, availableQuantity, avgRating, reviewCount, images, canReview, null);
    }

    /** Product 엔티티 + 전체 필드(재고·리뷰·이미지·canReview·wishlisted)를 포함한 응답으로 변환. */
    public static ProductResponse from(Product product, Integer availableQuantity,
                                       Double avgRating, Long reviewCount,
                                       List<ProductImageResponse> images, Boolean canReview,
                                       Boolean wishlisted) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .sku(product.getSku())
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .category(product.getCategoryName())
                .thumbnailUrl(product.getThumbnailUrl())
                .status(product.getStatus())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .availableQuantity(availableQuantity)
                .avgRating(avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : null)
                .reviewCount(reviewCount)
                .images(images)
                .canReview(canReview)
                .wishlisted(wishlisted)
                .build();
    }
}
