package com.stockmanagement.domain.order.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/** 주문 금액 미리보기 응답 DTO. */
@Getter
@Builder
public class OrderPreviewResponse {

    /** 쿠폰·포인트 적용 전 상품 합계 금액 */
    private final BigDecimal originalAmount;

    /** 쿠폰 할인 금액 (0이면 쿠폰 미적용) */
    private final BigDecimal couponDiscount;

    /** 포인트 할인 금액 (0이면 포인트 미사용) */
    private final BigDecimal pointDiscount;

    /** 배송비 (현재 무료배송 정책 — 0) */
    private final BigDecimal shippingFee;

    /** 최종 결제 금액 = originalAmount - couponDiscount - pointDiscount + shippingFee */
    private final BigDecimal finalAmount;

    /** 결제 완료 시 적립 예정 포인트 (finalAmount × 1%) */
    private final long earnablePoints;
}
