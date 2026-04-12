package com.stockmanagement.domain.order.service;

import com.stockmanagement.domain.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PENDING 상태로 일정 시간이 지난 주문을 자동으로 취소하는 스케줄러.
 *
 * <p>재고 예약({@code reserved})이 결제 없이 묶여 있는 상태를 해소해
 * 재고 누수를 방지한다.
 *
 * <p>기본값: 30분 초과 PENDING 주문을 5분마다 확인하여 취소.
 * 설정: {@code order.expiry.minutes}, {@code order.expiry.check-interval-ms}
 *
 * <p>{@code order.expiry.enabled=false}로 비활성화 가능 (통합 테스트 등).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "order.expiry.enabled", havingValue = "true", matchIfMissing = true)
public class OrderExpiryScheduler {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Value("${order.expiry.minutes:30}")
    private int expiryMinutes;

    @Scheduled(fixedDelayString = "${order.expiry.check-interval-ms:300000}")
    public void cancelExpiredOrders() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(expiryMinutes);
        List<Long> expiredIds = orderRepository.findExpiredPendingOrderIds(threshold);

        if (expiredIds.isEmpty()) {
            return;
        }

        log.info("만료 주문 자동 취소 시작 — 대상: {}건 (기준: {}분 초과)", expiredIds.size(), expiryMinutes);

        int cancelled = 0;
        for (Long orderId : expiredIds) {
            try {
                orderService.cancel(orderId, null, true); // 시스템 자동 취소 — ADMIN bypass, userId 불필요
                cancelled++;
                log.debug("주문 {} 자동 취소 완료", orderId);
            } catch (Exception e) {
                log.warn("주문 {} 자동 취소 실패", orderId, e);
            }
        }

        log.info("만료 주문 자동 취소 완료 — 성공: {}건 / 전체: {}건", cancelled, expiredIds.size());
    }
}
