package com.stockmanagement.domain.coupon.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사용자가 공개 쿠폰 코드를 입력해 지갑에 등록하는 요청 DTO. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CouponClaimRequest {

    @NotBlank
    private String couponCode;
}
