package com.stockmanagement.common.event;

import com.stockmanagement.common.outbox.OutboxEventType;
import lombok.Getter;

import java.util.Map;

/** 주문 취소(환불 포함) 이벤트. */
@Getter
public class OrderCancelledEvent extends DomainEvent implements OutboxSupport {

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

    @Override
    public OutboxEventType outboxEventType() {
        return OutboxEventType.ORDER_CANCELLED;
    }

    @Override
    public Map<String, Object> toOutboxPayload() {
        return Map.of("orderId", orderId, "userId", userId, "reason", reason);
    }
}
