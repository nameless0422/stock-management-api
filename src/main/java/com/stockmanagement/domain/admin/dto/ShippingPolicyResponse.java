package com.stockmanagement.domain.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 배송비 정책 응답 DTO. */
public record ShippingPolicyResponse(
        BigDecimal defaultFee,
        BigDecimal freeShippingThreshold,
        String updatedBy,
        LocalDateTime updatedAt
) {
    public static ShippingPolicyResponse of(BigDecimal defaultFee, BigDecimal freeShippingThreshold,
                                            String updatedBy, LocalDateTime updatedAt) {
        return new ShippingPolicyResponse(defaultFee, freeShippingThreshold, updatedBy, updatedAt);
    }
}
