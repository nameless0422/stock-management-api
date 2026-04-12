package com.stockmanagement.domain.coupon.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/** 쿠폰 유효성 검사 결과 + 할인 금액 미리보기. */
@Getter
@Builder
public class CouponValidateResponse {

    private Long couponId;
    private String couponCode;
    private String couponName;
    private BigDecimal orderAmount;
    private BigDecimal discountAmount;
    /** 쿠폰 적용 후 실결제 금액 (orderAmount - discountAmount). */
    private BigDecimal finalAmount;
}
