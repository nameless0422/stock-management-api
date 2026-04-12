package com.stockmanagement.common.event;

import com.stockmanagement.common.outbox.OutboxEventType;
import lombok.Getter;

import java.util.Map;

/** 배송 완료 이벤트 — ShipmentService.completeDelivery() 완료 후 발행 */
@Getter
public class ShipmentDeliveredEvent extends DomainEvent implements OutboxSupport {

    private final Long orderId;

    public ShipmentDeliveredEvent(Long orderId) {
        super();
        this.orderId = orderId;
    }

    @Override
    public OutboxEventType outboxEventType() {
        return OutboxEventType.SHIPMENT_DELIVERED;
    }

    @Override
    public Map<String, Object> toOutboxPayload() {
        return Map.of("orderId", orderId);
    }
}
