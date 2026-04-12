package com.stockmanagement.domain.coupon.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 쿠폰 유효성 검사 + 할인 금액 미리보기 요청. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CouponValidateRequest {

    @NotBlank
    private String couponCode;

    @NotNull
    @DecimalMin(value = "0.01", message = "주문 금액은 0보다 커야 합니다")
    private BigDecimal orderAmount;
}
