package com.stockmanagement.domain.order.entity;

/**
 * 주문 항목 상태.
 *
 * <ul>
 *   <li>{@code ACTIVE} — 정상 (기본값)
 *   <li>{@code CANCELLED} — 부분 취소됨
 * </ul>
 */
public enum OrderItemStatus {
    ACTIVE,
    CANCELLED
}
