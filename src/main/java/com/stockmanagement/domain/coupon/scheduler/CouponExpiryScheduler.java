package com.stockmanagement.domain.coupon.scheduler;

import com.stockmanagement.domain.coupon.entity.Coupon;
import com.stockmanagement.domain.coupon.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 만료 쿠폰 자동 비활성화 스케줄러.
 *
 * <p>매일 새벽 1시에 {@code validUntil < now}인 활성 쿠폰을 {@code active=false}로 전환한다.
 * Dirty Checking에 의해 별도 save() 없이 트랜잭션 종료 시 자동 반영된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "coupon.expiry.enabled", havingValue = "true", matchIfMissing = true)
public class CouponExpiryScheduler {

    private final CouponRepository couponRepository;

    @Scheduled(cron = "${coupon.expiry.cron:0 0 1 * * *}")
    @Transactional
    public void deactivateExpiredCoupons() {
        LocalDateTime now = LocalDateTime.now();
        List<Coupon> expired = couponRepository.findExpiredActiveCoupons(now);
        log.info("[CouponExpiryScheduler] 만료 쿠폰 비활성화 시작 — 대상: {}건", expired.size());

        int success = 0;
        for (Coupon coupon : expired) {
            try {
                coupon.deactivate(); // Dirty Checking → 트랜잭션 종료 시 자동 UPDATE
                success++;
            } catch (Exception e) {
                log.error("[CouponExpiryScheduler] 쿠폰 비활성화 실패 — couponId={}, error={}",
                        coupon.getId(), e.getMessage());
            }
        }
        log.info("[CouponExpiryScheduler] 완료 — 성공: {}건 / 전체: {}건", success, expired.size());
    }
}
