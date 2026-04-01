package com.stockmanagement.domain.coupon.repository;

import com.stockmanagement.domain.coupon.entity.CouponUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CouponUsageRepository extends JpaRepository<CouponUsage, Long> {

    /** 사용자별 쿠폰 사용 횟수 확인 (단건). */
    int countByCoupon_IdAndUserId(Long couponId, Long userId);

    /**
     * getMyCoupons() 배치 최적화용 — 여러 쿠폰의 사용 횟수를 한 번에 조회.
     * 결과: [couponId (Long), usedCount (Long)] 쌍의 리스트.
     */
    @Query("SELECT cu.coupon.id, COUNT(cu) FROM CouponUsage cu " +
           "WHERE cu.coupon.id IN :couponIds AND cu.userId = :userId " +
           "GROUP BY cu.coupon.id")
    List<Object[]> countByCouponIdsAndUserId(
            @Param("couponIds") List<Long> couponIds,
            @Param("userId") Long userId);

    /** 주문 취소 시 쿠폰 반환을 위한 조회. */
    Optional<CouponUsage> findByOrderId(Long orderId);
}
