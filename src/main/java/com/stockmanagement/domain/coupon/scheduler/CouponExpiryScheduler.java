package com.stockmanagement.domain.coupon.scheduler;

import com.stockmanagement.domain.coupon.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 만료 쿠폰 자동 비활성화 스케줄러.
 *
 * <p>매일 새벽 1시에 {@code validUntil < now}인 활성 쿠폰을 {@code active=false}로 전환한다.
 * 단일 벌크 UPDATE 쿼리로 처리하여 만료 쿠폰 수에 관계없이 DB 왕복 1회로 완료된다.
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
        int count = couponRepository.deactivateExpiredCoupons(now);
        log.info("[CouponExpiryScheduler] 만료 쿠폰 비활성화 완료 — {}건", count);
    }
}
