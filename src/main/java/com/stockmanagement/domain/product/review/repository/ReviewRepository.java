package com.stockmanagement.domain.product.review.repository;

import com.stockmanagement.domain.product.review.entity.Review;
import com.stockmanagement.domain.product.review.repository.ReviewStatsProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** 상품의 리뷰 목록을 최신순으로 페이징 조회한다. */
    Page<Review> findByProductIdOrderByCreatedAtDesc(Long productId, Pageable pageable);

    /** 특정 상품에 대한 사용자의 리뷰가 이미 존재하는지 확인한다 (1인 1리뷰 검증). */
    boolean existsByProductIdAndUserId(Long productId, Long userId);

    /** 특정 상품·사용자의 리뷰를 단건 조회한다 (삭제 권한 확인용). */
    Optional<Review> findByIdAndUserId(Long id, Long userId);

    /** 상품 평균 별점 계산용 */
    @Query("SELECT COALESCE(AVG(r.rating), 0.0) FROM Review r WHERE r.productId = :productId")
    double avgRatingByProductId(@Param("productId") Long productId);

    /** 상품 리뷰 수 */
    long countByProductId(Long productId);

    /**
     * 단일 상품의 평균 별점·리뷰 수를 조회한다 (단건 상세 조회용).
     *
     * <p>기존 avgRatingByProductId() + countByProductId() 2개 쿼리를 1개로 대체한다.
     * 리뷰가 없으면 GROUP BY 결과가 없으므로 Optional.empty()를 반환한다.
     */
    @Query("SELECT r.productId AS productId, AVG(r.rating) AS avgRating, COUNT(r) AS reviewCount " +
           "FROM Review r WHERE r.productId = :productId GROUP BY r.productId")
    Optional<ReviewStatsProjection> findReviewStatsByProductId(@Param("productId") Long productId);

    /** 여러 상품의 평균 별점·리뷰 수를 한 번에 조회한다 (목록 조회 N+1 방지). */
    @Query("SELECT r.productId AS productId, AVG(r.rating) AS avgRating, COUNT(r) AS reviewCount " +
           "FROM Review r WHERE r.productId IN :productIds GROUP BY r.productId")
    List<ReviewStatsProjection> findReviewStatsByProductIdIn(@Param("productIds") Collection<Long> productIds);

    /** 특정 사용자가 특정 주문 상품들에 대해 리뷰를 작성한 상품 ID 목록을 반환한다. */
    @Query("SELECT r.productId FROM Review r WHERE r.userId = :userId AND r.productId IN :productIds")
    List<Long> findReviewedProductIdsByUserId(@Param("userId") Long userId,
                                              @Param("productIds") Collection<Long> productIds);
}
