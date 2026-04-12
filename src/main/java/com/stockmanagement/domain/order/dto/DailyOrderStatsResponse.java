package com.stockmanagement.domain.order.dto;

import com.stockmanagement.domain.order.entity.DailyOrderStats;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 일별 주문·매출 통계 응답 DTO. */
@Getter
@Builder
public class DailyOrderStatsResponse {

    private Long id;
    private LocalDate statDate;
    private int totalOrders;
    private int confirmedOrders;
    private int cancelledOrders;
    private BigDecimal totalRevenue;

    public static DailyOrderStatsResponse from(DailyOrderStats stats) {
        return DailyOrderStatsResponse.builder()
                .id(stats.getId())
                .statDate(stats.getStatDate())
                .totalOrders(stats.getTotalOrders())
                .confirmedOrders(stats.getConfirmedOrders())
                .cancelledOrders(stats.getCancelledOrders())
                .totalRevenue(stats.getTotalRevenue())
                .build();
    }
}
