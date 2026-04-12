package com.stockmanagement.domain.admin.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardResponse(
        long totalOrders,
        long pendingOrders,
        long confirmedOrders,
        long cancelledOrders,
        BigDecimal totalRevenue,
        long totalUsers,
        List<LowStockItem> lowStockItems
) {
}
