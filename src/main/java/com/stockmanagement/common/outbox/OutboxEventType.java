package com.stockmanagement.common.outbox;

/** Outbox 테이블에 저장되는 도메인 이벤트 종류. */
public enum OutboxEventType {
    ORDER_CREATED,
    ORDER_CANCELLED,
    PAYMENT_CONFIRMED,
    SHIPMENT_SHIPPED,
    SHIPMENT_DELIVERED,
    /** 결제 확정 후 배송 레코드 생성 요청 — 실패 시 릴레이 스케줄러가 재시도한다. */
    SHIPMENT_CREATE,
    /** 결제 확정 후 포인트 적립 요청 — 실패 시 릴레이 스케줄러가 재시도한다. */
    POINT_EARN
}
