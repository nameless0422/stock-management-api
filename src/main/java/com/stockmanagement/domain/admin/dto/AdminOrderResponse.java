package com.stockmanagement.domain.admin.dto;

import com.stockmanagement.domain.order.entity.Order;
import com.stockmanagement.domain.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 관리자 전용 주문 응답 DTO.
 * 기본 OrderResponse에 사용자명(username)을 추가로 포함한다.
 */
public record AdminOrderResponse(
        Long id,
        Long userId,
        String username,
        OrderStatus status,
        BigDecimal totalAmount,
        LocalDateTime createdAt
) {
    public static AdminOrderResponse from(Order order, String username) {
        return new AdminOrderResponse(
                order.getId(),
                order.getUserId(),
                username,
                order.getStatus(),
                order.getTotalAmount(),
                order.getCreatedAt()
        );
    }
}
