package com.stockmanagement.domain.order.dto;

import com.stockmanagement.domain.order.entity.Order;
import com.stockmanagement.domain.order.entity.OrderStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 응답 DTO.
 *
 * <p>{@link Order} 엔티티와 하위 {@link com.stockmanagement.domain.order.entity.OrderItem} 목록을
 * 클라이언트에게 노출할 형태로 변환한다.
 * {@code @Jacksonized}로 Redis 캐시 역직렬화를 지원한다.
 */
@Getter
@Builder
@Jacksonized
public class OrderResponse {

    private final Long id;
    private final Long userId;
    private final OrderStatus status;
    private final BigDecimal totalAmount;
    private final String idempotencyKey;

    /** 선택된 배송지 ID — null이면 배송지 미지정 */
    private final Long deliveryAddressId;

    /** 주문 항목 목록 */
    private final List<OrderItemResponse> items;

    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    /** Order 엔티티를 응답 DTO로 변환하는 정적 팩토리 메서드 */
    public static OrderResponse from(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .idempotencyKey(order.getIdempotencyKey())
                .deliveryAddressId(order.getDeliveryAddressId())
                .items(order.getItems().stream()
                        .map(OrderItemResponse::from)
                        .toList())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
