package com.stockmanagement.domain.point.entity;

/** 포인트 트랜잭션 확정 상태 */
public enum PointTransactionStatus {
    /** 적립 예정 (배송 완료 전) */
    PENDING,
    /** 확정 (잔액에 반영됨) */
    CONFIRMED,
    /** 만료/취소 (주문 취소 등) */
    EXPIRED
}
