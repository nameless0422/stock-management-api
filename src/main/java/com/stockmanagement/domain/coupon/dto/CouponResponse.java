package com.stockmanagement.domain.coupon.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stockmanagement.domain.coupon.entity.Coupon;
import com.stockmanagement.domain.coupon.entity.DiscountType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class CouponResponse {

    private Long id;
    private String code;
    private String name;
    private String description;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal minimumOrderAmount;
    private BigDecimal maxDiscountAmount;
    private Integer maxUsageCount;
    private int usageCount;
    private int maxUsagePerUser;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private boolean active;
    @JsonProperty("isPublic")
    private boolean isPublic;
    private LocalDateTime createdAt;

    public static CouponResponse from(Coupon coupon) {
        return CouponResponse.builder()
                .id(coupon.getId())
                .code(coupon.getCode())
                .name(coupon.getName())
                .description(coupon.getDescription())
                .discountType(coupon.getDiscountType())
                .discountValue(coupon.getDiscountValue())
                .minimumOrderAmount(coupon.getMinimumOrderAmount())
                .maxDiscountAmount(coupon.getMaxDiscountAmount())
                .maxUsageCount(coupon.getMaxUsageCount())
                .usageCount(coupon.getUsageCount())
                .maxUsagePerUser(coupon.getMaxUsagePerUser())
                .validFrom(coupon.getValidFrom())
                .validUntil(coupon.getValidUntil())
                .active(coupon.isActive())
                .isPublic(coupon.isPublic())
                .createdAt(coupon.getCreatedAt())
                .build();
    }
}
