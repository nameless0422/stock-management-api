package com.stockmanagement.domain.product.review.repository;

/** 상품별 리뷰 통계 프로젝션 (목록 배치 조회용). */
public interface ReviewStatsProjection {
    Long getProductId();
    Double getAvgRating();
    Long getReviewCount();
}
