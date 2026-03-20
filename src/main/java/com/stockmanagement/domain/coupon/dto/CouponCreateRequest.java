package com.stockmanagement.domain.coupon.dto;

import com.stockmanagement.domain.coupon.entity.DiscountType;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class CouponCreateRequest {

    @NotBlank
    @Size(max = 50)
    private String code;

    @NotBlank
    @Size(max = 100)
    private String name;

    @Size(max = 255)
    private String description;

    @NotNull
    private DiscountType discountType;

    /** FIXED_AMOUNT: 할인 금액(원) / PERCENTAGE: 할인율(1~100). */
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal discountValue;

    /** null 허용 — 최소 주문 금액 제한 없음. */
    @DecimalMin("0")
    private BigDecimal minimumOrderAmount;

    /** null 허용 — PERCENTAGE 할인 상한 없음. */
    @DecimalMin("0")
    private BigDecimal maxDiscountAmount;

    /** null 허용 — 무제한 사용. */
    @Min(1)
    private Integer maxUsageCount;

    @NotNull
    @Min(1)
    private Integer maxUsagePerUser;

    @NotNull
    private LocalDateTime validFrom;

    @NotNull
    private LocalDateTime validUntil;
}
