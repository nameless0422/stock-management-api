package com.stockmanagement.common.event;

import com.stockmanagement.common.outbox.OutboxEventType;
import lombok.Getter;

import java.util.Map;

/** 반품 처리 이벤트 — ShipmentService.processReturn() 완료 후 발행 */
@Getter
public class ShipmentReturnedEvent extends DomainEvent implements OutboxSupport {

    private final Long orderId;

    public ShipmentReturnedEvent(Long orderId) {
        super();
        this.orderId = orderId;
    }

    @Override
    public OutboxEventType outboxEventType() {
        return OutboxEventType.SHIPMENT_RETURNED;
    }

    @Override
    public Map<String, Object> toOutboxPayload() {
        return Map.of("orderId", orderId);
    }
}
