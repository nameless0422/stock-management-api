package com.stockmanagement.common.event;

import com.stockmanagement.common.outbox.OutboxEventType;
import lombok.Getter;

import java.util.Map;

/** 결제 확정 후 포인트 적립을 요청하는 이벤트. */
@Getter
public class PointEarnEvent extends DomainEvent implements OutboxSupport {

    private final Long userId;
    private final long paidAmount;
    private final Long orderId;

    public PointEarnEvent(Long userId, long paidAmount, Long orderId) {
        super();
        this.userId = userId;
        this.paidAmount = paidAmount;
        this.orderId = orderId;
    }

    @Override
    public OutboxEventType outboxEventType() {
        return OutboxEventType.POINT_EARN;
    }

    @Override
    public Map<String, Object> toOutboxPayload() {
        return Map.of("userId", userId, "paidAmount", paidAmount, "orderId", orderId);
    }
}
