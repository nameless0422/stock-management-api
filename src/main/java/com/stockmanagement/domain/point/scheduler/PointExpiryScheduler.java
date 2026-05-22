package com.stockmanagement.domain.point.scheduler;

import com.stockmanagement.domain.point.service.PointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 만료일 경과 포인트 자동 소멸 스케줄러.
 *
 * <p>매일 새벽 0시 30분에 CONFIRMED 상태이면서 {@code expiresAt < now}인
 * 적립 포인트를 EXPIRED로 전환하고 잔액에서 차감한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "point.expiry.enabled", havingValue = "true", matchIfMissing = true)
public class PointExpiryScheduler {

    private final PointService pointService;

    @Scheduled(cron = "${point.expiry.cron:0 30 0 * * *}")
    public void expirePoints() {
        int count = pointService.expireBySchedule();
        log.info("[PointExpiryScheduler] 만료 포인트 처리 완료 — {}건", count);
    }
}
