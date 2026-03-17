package com.stockmanagement.domain.order.dto;

import com.stockmanagement.domain.order.entity.OrderStatus;
import com.stockmanagement.domain.order.entity.OrderStatusHistory;

import java.time.LocalDateTime;

public record OrderStatusHistoryResponse(
        Long id,
        Long orderId,
        OrderStatus fromStatus,
        OrderStatus toStatus,
        String changedBy,
        String note,
        LocalDateTime createdAt
) {
    public static OrderStatusHistoryResponse from(OrderStatusHistory h) {
        return new OrderStatusHistoryResponse(
                h.getId(), h.getOrderId(), h.getFromStatus(), h.getToStatus(),
                h.getChangedBy(), h.getNote(), h.getCreatedAt());
    }
}
