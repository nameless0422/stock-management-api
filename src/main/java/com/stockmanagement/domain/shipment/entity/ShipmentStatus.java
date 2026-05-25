package com.stockmanagement.domain.shipment.entity;

/**
 * 배송 상태 enum.
 *
 * <pre>
 * PREPARING  — 배송 준비 중 (주문 CONFIRMED 시 자동 생성)
 * SHIPPED    — 배송 출고 완료 (운송장 등록)
 * DELIVERED        — 배송 완료
 * RETURN_REQUESTED — 사용자 반품 신청 (ADMIN 승인/거부 대기)
 * RETURNED         — 반품 처리 완료
 * </pre>
 */
public enum ShipmentStatus {
    PREPARING, SHIPPED, DELIVERED, RETURN_REQUESTED, RETURNED
}
