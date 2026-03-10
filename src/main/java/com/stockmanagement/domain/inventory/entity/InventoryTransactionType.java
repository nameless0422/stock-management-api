package com.stockmanagement.domain.inventory.entity;

/**
 * 재고 이력 이벤트 유형.
 */
public enum InventoryTransactionType {
    /** 입고 */
    RECEIVE,
    /** 주문 생성 시 예약 */
    RESERVE,
    /** 주문 취소 또는 결제 실패 시 예약 해제 */
    RELEASE_RESERVATION,
    /** 결제 완료 후 출고 확정 */
    CONFIRM_ALLOCATION,
    /** 환불 처리 시 확정 해제 */
    RELEASE_ALLOCATION
}
