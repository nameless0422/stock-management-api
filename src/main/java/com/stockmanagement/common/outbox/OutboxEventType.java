package com.stockmanagement.common.outbox;

/** Outbox 테이블에 저장되는 도메인 이벤트 종류. */
public enum OutboxEventType {
    ORDER_CREATED,
    ORDER_CANCELLED,
    PAYMENT_CONFIRMED,
    SHIPMENT_SHIPPED,
    SHIPMENT_DELIVERED
}
