package com.stockmanagement.common.event;

import lombok.Getter;

import java.math.BigDecimal;

/** 주문 생성 완료 이벤트. */
@Getter
public class OrderCreatedEvent extends DomainEvent {

    private final Long orderId;
    private final Long userId;
    private final BigDecimal totalAmount;

    public OrderCreatedEvent(Long orderId, Long userId, BigDecimal totalAmount) {
        super();
        this.orderId = orderId;
        this.userId = userId;
        this.totalAmount = totalAmount;
    }
}
