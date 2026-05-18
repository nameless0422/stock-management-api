package com.stockmanagement.domain.product.dto;

import com.stockmanagement.domain.product.category.dto.CategoryResponse;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/**
 * 홈 화면 집계 응답 DTO.
 *
 * <p>신상품, 인기 상품, 추천 카테고리를 단일 응답으로 반환한다.
 * Redis 캐시(TTL 5분)를 통해 DB 부하를 최소화한다.
 */
@Getter
@Builder
@Jacksonized
public class HomeResponse {

    /** 최신 ACTIVE 상품 (최대 8개, createdAt DESC) */
    private final List<ProductResponse> newArrivals;

    /** 인기 ACTIVE 상품 (최대 8개, 리뷰 수 DESC) */
    private final List<ProductResponse> popularProducts;

    /** 추천 카테고리 트리 (루트 카테고리 + 하위) */
    private final List<CategoryResponse> featuredCategories;
}
