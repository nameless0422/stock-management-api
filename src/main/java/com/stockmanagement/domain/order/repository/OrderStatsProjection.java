package com.stockmanagement.domain.order.repository;

import com.stockmanagement.domain.order.entity.OrderStatus;

import java.math.BigDecimal;

/**
 * 주문 상태별 통계 Projection.
 *
 * <p>{@code findOrderStats()} 쿼리의 결과를 담는 인터페이스.
 * GROUP BY status 단일 쿼리로 상태별 주문 수·금액을 한 번에 조회한다.
 */
public interface OrderStatsProjection {
    OrderStatus getStatus();
    long getOrderCount();
    BigDecimal getTotalAmount();
}
