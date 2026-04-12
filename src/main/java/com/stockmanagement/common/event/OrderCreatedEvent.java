package com.stockmanagement.common.event;

import com.stockmanagement.common.outbox.OutboxEventType;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Map;

/** 주문 생성 완료 이벤트. */
@Getter
public class OrderCreatedEvent extends DomainEvent implements OutboxSupport {

    private final Long orderId;
    private final Long userId;
    private final BigDecimal totalAmount;

    public OrderCreatedEvent(Long orderId, Long userId, BigDecimal totalAmount) {
        super();
        this.orderId = orderId;
        this.userId = userId;
        this.totalAmount = totalAmount;
    }

    @Override
    public OutboxEventType outboxEventType() {
        return OutboxEventType.ORDER_CREATED;
    }

    @Override
    public Map<String, Object> toOutboxPayload() {
        return Map.of("orderId", orderId, "userId", userId, "totalAmount", totalAmount.toPlainString());
    }
}
