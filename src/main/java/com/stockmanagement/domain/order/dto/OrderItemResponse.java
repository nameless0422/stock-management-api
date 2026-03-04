package com.stockmanagement.domain.order.dto;

import com.stockmanagement.domain.order.entity.OrderItem;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 주문 항목 응답 DTO.
 *
 * <p>{@link OrderItem} 엔티티를 클라이언트에게 노출할 형태로 변환한다.
 */
@Getter
public class OrderItemResponse {

    private final Long id;
    private final Long productId;

    /** 상품명 — 주문 당시 이름 (Product 참조를 통해 현재 이름을 반환) */
    private final String productName;

    /** 주문 수량 */
    private final int quantity;

    /** 주문 당시 단가 */
    private final BigDecimal unitPrice;

    /** 소계 = unitPrice × quantity */
    private final BigDecimal subtotal;

    private OrderItemResponse(OrderItem item) {
        this.id = item.getId();
        this.productId = item.getProduct().getId();
        this.productName = item.getProduct().getName();
        this.quantity = item.getQuantity();
        this.unitPrice = item.getUnitPrice();
        this.subtotal = item.getSubtotal();
    }

    /** OrderItem 엔티티를 응답 DTO로 변환하는 정적 팩토리 메서드 */
    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item);
    }
}
