package com.stockmanagement.domain.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 일별 주문·매출 통계 엔티티.
 *
 * <p>{@code DailyOrderStatsScheduler}가 매일 자정 1분에 전일 기준으로 집계한다.
 * {@code statDate} 는 UNIQUE 제약이 있어 동일 날짜에 대해 중복 생성되지 않는다.
 */
@Entity
@Table(name = "daily_order_stats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyOrderStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 집계 기준일 (전일) */
    @Column(nullable = false, unique = true)
    private LocalDate statDate;

    /** 전일 전체 주문 수 */
    @Column(nullable = false)
    private int totalOrders;

    /** 전일 결제 완료(CONFIRMED) 주문 수 */
    @Column(nullable = false)
    private int confirmedOrders;

    /** 전일 취소 주문 수 */
    @Column(nullable = false)
    private int cancelledOrders;

    /** 전일 매출액 (CONFIRMED 기준, 쿠폰 할인 차감) */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalRevenue;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private DailyOrderStats(LocalDate statDate, int totalOrders, int confirmedOrders,
                            int cancelledOrders, BigDecimal totalRevenue) {
        this.statDate        = statDate;
        this.totalOrders     = totalOrders;
        this.confirmedOrders = confirmedOrders;
        this.cancelledOrders = cancelledOrders;
        this.totalRevenue    = totalRevenue;
    }

    /** 이미 존재하는 레코드를 최신 집계값으로 갱신한다 (멱등성 보장). */
    public void update(int totalOrders, int confirmedOrders, int cancelledOrders,
                       BigDecimal totalRevenue) {
        this.totalOrders     = totalOrders;
        this.confirmedOrders = confirmedOrders;
        this.cancelledOrders = cancelledOrders;
        this.totalRevenue    = totalRevenue;
    }
}
