package com.stockmanagement.domain.coupon.repository;

import com.stockmanagement.domain.coupon.entity.Coupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    /** 만료됐지만 아직 활성 상태인 쿠폰 목록 (CouponExpiryScheduler 사용) */
    @Query("SELECT c FROM Coupon c WHERE c.validUntil < :now AND c.active = true")
    List<Coupon> findExpiredActiveCoupons(@Param("now") LocalDateTime now);

    Optional<Coupon> findByCode(String code);

    /** 쿠폰 사용 처리 시 비관적 락 적용 — usageCount 동시 증가 방지. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Coupon c WHERE c.code = :code")
    Optional<Coupon> findByCodeWithLock(@Param("code") String code);
}
