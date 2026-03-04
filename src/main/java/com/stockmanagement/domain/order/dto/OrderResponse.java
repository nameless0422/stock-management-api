package com.stockmanagement.domain.order.dto;

import com.stockmanagement.domain.order.entity.Order;
import com.stockmanagement.domain.order.entity.OrderStatus;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 응답 DTO.
 *
 * <p>{@link Order} 엔티티와 하위 {@link com.stockmanagement.domain.order.entity.OrderItem} 목록을
 * 클라이언트에게 노출할 형태로 변환한다.
 */
@Getter
public class OrderResponse {

    private final Long id;
    private final Long userId;
    private final OrderStatus status;
    private final BigDecimal totalAmount;
    private final String idempotencyKey;

    /** 주문 항목 목록 */
    private final List<OrderItemResponse> items;

    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private OrderResponse(Order order) {
        this.id = order.getId();
        this.userId = order.getUserId();
        this.status = order.getStatus();
        this.totalAmount = order.getTotalAmount();
        this.idempotencyKey = order.getIdempotencyKey();
        this.items = order.getItems().stream()
                .map(OrderItemResponse::from)
                .toList();
        this.createdAt = order.getCreatedAt();
        this.updatedAt = order.getUpdatedAt();
    }

    /** Order 엔티티를 응답 DTO로 변환하는 정적 팩토리 메서드 */
    public static OrderResponse from(Order order) {
        return new OrderResponse(order);
    }
}
