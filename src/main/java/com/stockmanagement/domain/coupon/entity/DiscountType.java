package com.stockmanagement.domain.coupon.entity;

/** 쿠폰 할인 방식. */
public enum DiscountType {
    /** 고정 금액 할인 — discountValue = 할인 금액(원) */
    FIXED_AMOUNT,
    /** 퍼센트 할인 — discountValue = 할인율(0~100), maxDiscountAmount로 상한 설정 가능 */
    PERCENTAGE
}
