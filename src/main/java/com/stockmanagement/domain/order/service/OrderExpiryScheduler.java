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
 * 만료·고착 주문을 자동으로 정리하는 스케줄러.
 *
 * <ul>
 *   <li>PENDING 상태로 일정 시간({@code order.expiry.minutes}, 기본 30분)이 지난 주문 → 자동 취소
 *   <li>PAYMENT_IN_PROGRESS 상태로 일정 시간({@code order.payment-in-progress.timeout-minutes}, 기본 10분)이
 *       지난 주문 → PENDING으로 복원 (결제 오류 후 {@code resetOrderOnPaymentError} 실패로 고착된 경우 대응)
 * </ul>
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

    /** PAYMENT_IN_PROGRESS 상태 허용 최대 시간 (기본 10분). 초과 시 PENDING으로 복원. */
    @Value("${order.payment-in-progress.timeout-minutes:10}")
    private int paymentInProgressTimeoutMinutes;

    @Scheduled(fixedDelayString = "${order.expiry.check-interval-ms:300000}")
    public void cancelExpiredOrders() {
        resetStuckPaymentInProgressOrders();
        cancelExpiredPendingOrders();
    }

    /**
     * 결제 오류로 PAYMENT_IN_PROGRESS에 고착된 주문을 PENDING으로 복원한다.
     *
     * <p>복원된 주문은 다음 주기에 {@link #cancelExpiredPendingOrders}가 처리하거나
     * 사용자가 직접 결제를 재시도할 수 있다.
     */
    private void resetStuckPaymentInProgressOrders() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(paymentInProgressTimeoutMinutes);
        List<Long> stuckIds = orderRepository.findStuckPaymentInProgressOrderIds(threshold);

        if (stuckIds.isEmpty()) {
            return;
        }

        log.warn("고착된 PAYMENT_IN_PROGRESS 주문 복원 시작 — 대상: {}건 (기준: {}분 초과)",
                stuckIds.size(), paymentInProgressTimeoutMinutes);

        int reset = 0;
        int failed = 0;
        for (Long orderId : stuckIds) {
            try {
                orderService.resetPaymentInProgressBySystem(orderId);
                reset++;
                log.info("주문 {} PAYMENT_IN_PROGRESS → PENDING 복원 완료", orderId);
            } catch (Exception e) {
                failed++;
                log.error("주문 {} 복원 실패 — 수동 확인 필요", orderId, e);
            }
        }

        log.warn("PAYMENT_IN_PROGRESS 복원 완료 — 성공: {}건 / 실패: {}건 / 전체: {}건",
                reset, failed, stuckIds.size());
    }

    /** PENDING 상태로 만료 시간이 지난 주문을 자동 취소한다. */
    private void cancelExpiredPendingOrders() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(expiryMinutes);
        List<Long> expiredIds = orderRepository.findExpiredPendingOrderIds(threshold);

        if (expiredIds.isEmpty()) {
            return;
        }

        log.info("만료 주문 자동 취소 시작 — 대상: {}건 (기준: {}분 초과)", expiredIds.size(), expiryMinutes);

        int cancelled = 0;
        int failed = 0;
        for (Long orderId : expiredIds) {
            try {
                orderService.cancelBySystem(orderId);
                cancelled++;
                log.debug("주문 {} 자동 취소 완료", orderId);
            } catch (Exception e) {
                failed++;
                log.error("주문 {} 자동 취소 실패 — 수동 확인 필요", orderId, e);
            }
        }

        log.info("만료 주문 자동 취소 완료 — 성공: {}건 / 실패: {}건 / 전체: {}건",
                cancelled, failed, expiredIds.size());
    }
}
