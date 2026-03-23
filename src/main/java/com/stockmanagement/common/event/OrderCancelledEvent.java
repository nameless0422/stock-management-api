package com.stockmanagement.common.event;

import lombok.Getter;

/** 주문 취소(환불 포함) 이벤트. */
@Getter
public class OrderCancelledEvent extends DomainEvent {

    private final Long orderId;
    private final Long userId;
    /** 취소 사유 — cancel은 "PENDING_CANCELLED", refund는 "PAYMENT_REFUNDED" */
    private final String reason;

    public OrderCancelledEvent(Long orderId, Long userId, String reason) {
        super();
        this.orderId = orderId;
        this.userId = userId;
        this.reason = reason;
    }
}
