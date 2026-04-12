package com.stockmanagement.domain.coupon.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stockmanagement.domain.coupon.entity.DiscountType;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class CouponCreateRequest {

    /**
     * 쿠폰 코드.
     * 최소 8자, 영문 + 숫자 혼합 필수.
     * 단순 코드("AAAA", "12345678")는 무차별 대입으로 탈취 가능하므로 형식 강제.
     */
    @NotBlank
    @Size(min = 8, max = 50)
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z0-9]+$",
             message = "쿠폰 코드는 영문과 숫자를 모두 포함한 8자 이상이어야 합니다")
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

    /** 공개 쿠폰 여부. true이면 사용자가 직접 claim 가능. 기본값 false (admin 발급 필수). */
    @JsonProperty("isPublic")
    private boolean isPublic;
}
