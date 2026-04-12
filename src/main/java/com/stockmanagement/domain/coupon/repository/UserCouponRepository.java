package com.stockmanagement.domain.coupon.repository;

import com.stockmanagement.domain.coupon.entity.UserCoupon;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {

    /**
     * 사용자에게 발급된 쿠폰 목록을 조회한다.
     * {@code @EntityGraph}로 Coupon을 즉시 로딩하여 N+1 방지.
     */
    @EntityGraph(attributePaths = "coupon")
    List<UserCoupon> findByUserId(Long userId);

    boolean existsByUserIdAndCouponId(Long userId, Long couponId);
}
