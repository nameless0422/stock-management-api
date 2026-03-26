package com.stockmanagement.domain.coupon.repository;

import com.stockmanagement.domain.coupon.entity.UserCoupon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {

    List<UserCoupon> findByUserId(Long userId);

    boolean existsByUserIdAndCouponId(Long userId, Long couponId);
}
