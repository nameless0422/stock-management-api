package com.stockmanagement.domain.coupon.repository;

import com.stockmanagement.domain.coupon.entity.CouponUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CouponUsageRepository extends JpaRepository<CouponUsage, Long> {

    /** 사용자별 쿠폰 사용 횟수 확인. */
    int countByCoupon_IdAndUserId(Long couponId, Long userId);

    /** 주문 취소 시 쿠폰 반환을 위한 조회. */
    Optional<CouponUsage> findByOrderId(Long orderId);
}
