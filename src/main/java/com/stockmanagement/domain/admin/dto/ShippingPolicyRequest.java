package com.stockmanagement.domain.admin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** 배송비 정책 변경 요청 DTO. */
public record ShippingPolicyRequest(
        @NotNull(message = "기본 배송비는 필수입니다.")
        @DecimalMin(value = "0", message = "배송비는 0 이상이어야 합니다.")
        BigDecimal defaultFee,

        @NotNull(message = "무료배송 기준 금액은 필수입니다.")
        @DecimalMin(value = "0", message = "무료배송 기준 금액은 0 이상이어야 합니다.")
        BigDecimal freeShippingThreshold
) {
}
