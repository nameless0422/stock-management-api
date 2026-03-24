package com.stockmanagement.common.event;

import lombok.Getter;

/** 상품 출고 이벤트 — ShipmentService.startShipping() 완료 후 발행 */
@Getter
public class ShipmentShippedEvent extends DomainEvent {

    private final Long orderId;
    private final String carrier;
    private final String trackingNumber;

    public ShipmentShippedEvent(Long orderId, String carrier, String trackingNumber) {
        super();
        this.orderId = orderId;
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
    }
}
