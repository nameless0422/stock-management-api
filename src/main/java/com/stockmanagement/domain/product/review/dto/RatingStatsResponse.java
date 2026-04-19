package com.stockmanagement.domain.product.review.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/** 상품 리뷰 별점 분포 통계 응답 DTO. */
@Getter
@Builder
public class RatingStatsResponse {

    /** 평균 별점 (소수점 1자리) */
    private final double avgRating;

    /** 전체 리뷰 수 */
    private final long reviewCount;

    /** 별점별 리뷰 수 분포 — key: 1~5, value: 해당 별점 리뷰 수 */
    private final Map<Integer, Long> distribution;
}
