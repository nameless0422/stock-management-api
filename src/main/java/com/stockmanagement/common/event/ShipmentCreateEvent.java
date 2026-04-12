package com.stockmanagement.common.event;

import com.stockmanagement.common.outbox.OutboxEventType;
import lombok.Getter;

import java.util.Map;

/** 결제 확정 후 배송 레코드 생성을 요청하는 이벤트. */
@Getter
public class ShipmentCreateEvent extends DomainEvent implements OutboxSupport {

    private final Long orderId;

    public ShipmentCreateEvent(Long orderId) {
        super();
        this.orderId = orderId;
    }

    @Override
    public OutboxEventType outboxEventType() {
        return OutboxEventType.SHIPMENT_CREATE;
    }

    @Override
    public Map<String, Object> toOutboxPayload() {
        return Map.of("orderId", orderId);
    }
}
