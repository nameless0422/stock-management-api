package com.stockmanagement.domain.coupon.repository;

import com.stockmanagement.domain.coupon.entity.Coupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    /**
     * 만료된 활성 쿠폰을 단일 벌크 UPDATE로 비활성화한다.
     *
     * <p>기존 findExpiredActiveCoupons() + 건별 Dirty Checking 방식은
     * 만료 쿠폰 수만큼 개별 UPDATE 쿼리가 발생했음.
     * 단일 UPDATE 문으로 대체해 DB 왕복을 1회로 줄인다.
     *
     * @return 비활성화된 쿠폰 수
     */
    @Modifying
    @Query("UPDATE Coupon c SET c.active = false WHERE c.validUntil < :now AND c.active = true")
    int deactivateExpiredCoupons(@Param("now") LocalDateTime now);

    Optional<Coupon> findByCode(String code);

    /** 쿠폰 사용 처리 시 비관적 락 적용 — usageCount 동시 증가 방지. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Coupon c WHERE c.code = :code")
    Optional<Coupon> findByCodeWithLock(@Param("code") String code);

    /** 쿠폰 반환 처리 시 비관적 락 적용 — usageCount lost update 방지. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Coupon c WHERE c.id = :id")
    Optional<Coupon> findByIdWithLock(@Param("id") Long id);
}
