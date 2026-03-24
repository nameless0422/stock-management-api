package com.stockmanagement.domain.refund.entity;

/** 환불 처리 상태 */
public enum RefundStatus {
    /** 처리 중 */
    PENDING,
    /** 환불 완료 */
    COMPLETED,
    /** 환불 실패 */
    FAILED
}
