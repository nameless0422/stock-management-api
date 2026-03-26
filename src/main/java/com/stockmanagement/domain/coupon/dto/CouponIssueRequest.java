package com.stockmanagement.domain.coupon.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 관리자 쿠폰 발급 요청 DTO. */
@Getter
@NoArgsConstructor
public class CouponIssueRequest {

    @NotNull
    private Long userId;
}
