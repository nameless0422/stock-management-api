package com.stockmanagement.domain.order.entity;

/**
 * 주문 상태 열거형.
 *
 * <pre>
 * PENDING              → 주문 생성 완료, 재고 예약 완료, 결제 대기 중
 * PAYMENT_IN_PROGRESS  → Toss API 승인 요청 중 (만료 스케줄러 접근 불가)
 * CONFIRMED            → 결제 성공 (Payment 도메인에서 전환)
 * CANCELLED            → 주문 취소, 재고 예약 해제 완료
 * </pre>
 *
 * <p>상태 전이:
 * <ul>
 *   <li>PENDING → PAYMENT_IN_PROGRESS (Toss API 호출 직전 선점)
 *   <li>PAYMENT_IN_PROGRESS → CONFIRMED (결제 성공)
 *   <li>PAYMENT_IN_PROGRESS → PENDING (결제 실패/오류 시 복원)
 *   <li>PENDING → CONFIRMED (가상계좌 Webhook 경로)
 *   <li>PENDING → CANCELLED (주문 취소 / 만료 스케줄러)
 * </ul>
 * PAYMENT_IN_PROGRESS 상태는 만료 스케줄러({@code findExpiredPendingOrderIds})가
 * PENDING만 조회하므로 자동 취소 대상에서 제외된다.
 */
public enum OrderStatus {

    /** 주문 생성, 재고 예약 완료 — 결제 대기 상태 */
    PENDING,

    /**
     * Toss API 결제 승인 요청 중.
     *
     * <p>외부 HTTP 호출(최대 30초) 동안 만료 스케줄러가 주문을 취소하지 못하도록
     * {@link com.stockmanagement.domain.payment.service.PaymentTransactionHelper}가
     * Toss API 호출 직전에 이 상태로 전환한다. Stripe의 {@code processing},
     * Magento의 {@code payment_review}와 동일한 역할.
     */
    PAYMENT_IN_PROGRESS,

    /** 결제 완료 — 출고 확정 상태 */
    CONFIRMED,

    /** 주문 취소 — 재고 예약 해제 완료 */
    CANCELLED
}
