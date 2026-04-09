package com.stockmanagement.common.event;

import com.stockmanagement.common.outbox.OutboxEventType;
import lombok.Getter;

import java.util.Map;

/** 상품 출고 이벤트 — ShipmentService.startShipping() 완료 후 발행 */
@Getter
public class ShipmentShippedEvent extends DomainEvent implements OutboxSupport {

    private final Long orderId;
    private final String carrier;
    private final String trackingNumber;

    public ShipmentShippedEvent(Long orderId, String carrier, String trackingNumber) {
        super();
        this.orderId = orderId;
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
    }

    @Override
    public OutboxEventType outboxEventType() {
        return OutboxEventType.SHIPMENT_SHIPPED;
    }

    @Override
    public Map<String, Object> toOutboxPayload() {
        return Map.of("orderId", orderId, "carrier", carrier, "trackingNumber", trackingNumber);
    }
}
