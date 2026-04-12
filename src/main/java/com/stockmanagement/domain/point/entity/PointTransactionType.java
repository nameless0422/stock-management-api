package com.stockmanagement.domain.point.entity;

/** 포인트 변동 유형 */
public enum PointTransactionType {
    /** 구매 적립 */
    EARN,
    /** 주문 시 사용 */
    USE,
    /** 주문 취소/환불로 인한 반환 */
    REFUND,
    /** 유효기간 만료 소멸 */
    EXPIRE
}
