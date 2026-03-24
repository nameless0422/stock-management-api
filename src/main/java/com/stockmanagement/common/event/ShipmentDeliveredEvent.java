package com.stockmanagement.common.event;

import lombok.Getter;

/** 배송 완료 이벤트 — ShipmentService.completeDelivery() 완료 후 발행 */
@Getter
public class ShipmentDeliveredEvent extends DomainEvent {

    private final Long orderId;

    public ShipmentDeliveredEvent(Long orderId) {
        super();
        this.orderId = orderId;
    }
}
