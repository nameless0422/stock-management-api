package com.stockmanagement.domain.order.scheduler;

import com.stockmanagement.domain.order.entity.DailyOrderStats;
import com.stockmanagement.domain.order.entity.OrderStatus;
import com.stockmanagement.domain.order.repository.DailyOrderStatsRepository;
import com.stockmanagement.domain.order.repository.OrderRepository;
import com.stockmanagement.domain.order.repository.OrderStatsProjection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 일별 주문·매출 통계 스케줄러.
 *
 * <p>매일 자정 1분에 전일 주문을 집계하여 {@code daily_order_stats}에 저장한다.
 * 동일 날짜 레코드가 이미 존재하면 update하여 멱등성을 보장한다.
 *
 * <p>기존 4개 개별 쿼리(count×3 + sum×1) → {@code findOrderStatsBetween()} 단일 GROUP BY 쿼리로 대체.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "order.stats.enabled", havingValue = "true", matchIfMissing = true)
public class DailyOrderStatsScheduler {

    private final OrderRepository orderRepository;
    private final DailyOrderStatsRepository statsRepository;

    @Scheduled(cron = "${order.stats.cron:0 1 0 * * *}")
    @Transactional
    public void aggregateDailyStats() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDateTime start = yesterday.atStartOfDay();
        LocalDateTime end   = LocalDate.now().atStartOfDay(); // exclusive end: < today 00:00:00

        log.info("[DailyOrderStatsScheduler] 일별 통계 집계 시작 — 기준일: {}", yesterday);

        try {
            // 단일 GROUP BY 쿼리로 상태별 건수·매출액을 한 번에 조회 (기존 4개 쿼리 대체)
            List<OrderStatsProjection> statsList = orderRepository.findOrderStatsBetween(start, end);
            Map<OrderStatus, OrderStatsProjection> statsMap = statsList.stream()
                    .collect(Collectors.toMap(OrderStatsProjection::getStatus, s -> s));

            int totalOrders     = statsList.stream().mapToInt(s -> (int) s.getOrderCount()).sum();
            int confirmedOrders = statsMap.containsKey(OrderStatus.CONFIRMED)
                    ? (int) statsMap.get(OrderStatus.CONFIRMED).getOrderCount() : 0;
            int cancelledOrders = statsMap.containsKey(OrderStatus.CANCELLED)
                    ? (int) statsMap.get(OrderStatus.CANCELLED).getOrderCount() : 0;
            BigDecimal totalRevenue = statsMap.containsKey(OrderStatus.CONFIRMED)
                    ? statsMap.get(OrderStatus.CONFIRMED).getTotalAmount() : BigDecimal.ZERO;

            statsRepository.findByStatDate(yesterday)
                    .ifPresentOrElse(
                            existing -> {
                                existing.update(totalOrders, confirmedOrders, cancelledOrders, totalRevenue);
                                log.info("[DailyOrderStatsScheduler] 기존 레코드 업데이트 — date={}", yesterday);
                            },
                            () -> {
                                statsRepository.save(DailyOrderStats.builder()
                                        .statDate(yesterday)
                                        .totalOrders(totalOrders)
                                        .confirmedOrders(confirmedOrders)
                                        .cancelledOrders(cancelledOrders)
                                        .totalRevenue(totalRevenue)
                                        .build());
                                log.info("[DailyOrderStatsScheduler] 신규 레코드 저장 — date={}", yesterday);
                            });

            log.info("[DailyOrderStatsScheduler] 완료 — 전체: {}, 확정: {}, 취소: {}, 매출: {}",
                    totalOrders, confirmedOrders, cancelledOrders, totalRevenue);
        } catch (Exception e) {
            log.error("[DailyOrderStatsScheduler] 집계 실패 — date={}, error={}", yesterday, e.getMessage());
        }
    }
}
