package com.stockmanagement.domain.notification.entity;

/** 인앱 알림 유형. */
public enum NotificationType {
    ORDER_CREATED,
    ORDER_CANCELLED,
    PAYMENT_CONFIRMED,
    SHIPMENT_SHIPPED,
    SHIPMENT_DELIVERED
}
