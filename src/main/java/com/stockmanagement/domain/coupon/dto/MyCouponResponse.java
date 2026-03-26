package com.stockmanagement.domain.coupon.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stockmanagement.domain.coupon.entity.Coupon;
import com.stockmanagement.domain.coupon.entity.DiscountType;
import com.stockmanagement.domain.coupon.entity.UserCoupon;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 내 쿠폰 목록 응답 DTO (발급된 쿠폰 + 사용 가능 여부). */
@Getter
@Builder
public class MyCouponResponse {

    private final Long id;
    private final String code;
    private final String name;
    private final String description;
    private final DiscountType discountType;
    private final BigDecimal discountValue;
    private final BigDecimal minimumOrderAmount;
    private final BigDecimal maxDiscountAmount;
    private final LocalDateTime validFrom;
    private final LocalDateTime validUntil;
    /** 발급 일시 */
    private final LocalDateTime issuedAt;
    /** 현재 사용 가능 여부 (active + 기간 유효 + 수량 미소진 + 사용자 한도 미달) */
    @JsonProperty("isUsable")
    private final boolean isUsable;

    public static MyCouponResponse from(UserCoupon userCoupon, boolean isUsable) {
        Coupon c = userCoupon.getCoupon();
        return MyCouponResponse.builder()
                .id(c.getId())
                .code(c.getCode())
                .name(c.getName())
                .description(c.getDescription())
                .discountType(c.getDiscountType())
                .discountValue(c.getDiscountValue())
                .minimumOrderAmount(c.getMinimumOrderAmount())
                .maxDiscountAmount(c.getMaxDiscountAmount())
                .validFrom(c.getValidFrom())
                .validUntil(c.getValidUntil())
                .issuedAt(userCoupon.getIssuedAt())
                .isUsable(isUsable)
                .build();
    }
}
