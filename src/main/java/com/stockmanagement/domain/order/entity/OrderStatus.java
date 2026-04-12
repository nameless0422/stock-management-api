package com.stockmanagement.domain.order.entity;

/**
 * 주문 상태 열거형.
 *
 * <pre>
 * PENDING   → 주문 생성 완료, 재고 예약 완료, 결제 대기 중
 * CONFIRMED → 결제 성공 (Payment 도메인에서 전환)
 * CANCELLED → 주문 취소, 재고 예약 해제 완료
 * </pre>
 *
 * <p>상태 전이:
 * <ul>
 *   <li>PENDING → CONFIRMED (결제 성공)
 *   <li>PENDING → CANCELLED (주문 취소)
 * </ul>
 * CONFIRMED·CANCELLED 상태에서는 더 이상 전이가 발생하지 않는다.
 */
public enum OrderStatus {

    /** 주문 생성, 재고 예약 완료 — 결제 대기 상태 */
    PENDING,

    /** 결제 완료 — 출고 확정 상태 */
    CONFIRMED,

    /** 주문 취소 — 재고 예약 해제 완료 */
    CANCELLED
}
