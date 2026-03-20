package com.stockmanagement.domain.coupon.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 쿠폰 엔티티.
 *
 * <p>discountType에 따라 다른 할인 계산이 적용된다.
 * <ul>
 *   <li>FIXED_AMOUNT: {@code min(discountValue, orderAmount)}
 *   <li>PERCENTAGE: {@code min(orderAmount × discountValue/100, maxDiscountAmount)}
 * </ul>
 */
@Entity
@Table(name = "coupons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DiscountType discountType;

    /** 할인 금액(FIXED_AMOUNT) 또는 할인율(PERCENTAGE, 0~100). */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal discountValue;

    /** 최소 주문 금액 조건. null이면 제한 없음. */
    @Column(precision = 19, scale = 2)
    private BigDecimal minimumOrderAmount;

    /** 퍼센트 할인 시 최대 할인 금액 상한. null이면 제한 없음. */
    @Column(precision = 19, scale = 2)
    private BigDecimal maxDiscountAmount;

    /** 전체 사용 가능 횟수 제한. null이면 무제한. */
    @Column
    private Integer maxUsageCount;

    /** 현재 누적 사용 횟수. */
    @Column(nullable = false)
    private int usageCount;

    /** 사용자별 사용 가능 횟수 (기본 1회). */
    @Column(nullable = false)
    private int maxUsagePerUser;

    @Column(nullable = false)
    private LocalDateTime validFrom;

    @Column(nullable = false)
    private LocalDateTime validUntil;

    @Column(nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Coupon(String code, String name, String description,
                  DiscountType discountType, BigDecimal discountValue,
                  BigDecimal minimumOrderAmount, BigDecimal maxDiscountAmount,
                  Integer maxUsageCount, int maxUsagePerUser,
                  LocalDateTime validFrom, LocalDateTime validUntil) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.minimumOrderAmount = minimumOrderAmount;
        this.maxDiscountAmount = maxDiscountAmount;
        this.maxUsageCount = maxUsageCount;
        this.usageCount = 0;
        this.maxUsagePerUser = maxUsagePerUser;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.active = true;
    }

    /** 사용 횟수를 1 증가시킨다. */
    public void increaseUsage() {
        this.usageCount++;
    }

    /** 사용 횟수를 1 감소시킨다. 주문 취소 시 호출. */
    public void decreaseUsage() {
        if (this.usageCount > 0) {
            this.usageCount--;
        }
    }

    /** 쿠폰을 비활성화한다. */
    public void deactivate() {
        this.active = false;
    }

    /**
     * 주문 금액에 대한 실제 할인 금액을 계산한다.
     *
     * @param orderAmount 쿠폰 적용 전 주문 금액
     * @return 실제 할인 금액 (주문 금액을 초과하지 않음)
     */
    public BigDecimal calculateDiscount(BigDecimal orderAmount) {
        BigDecimal discount;
        if (discountType == DiscountType.FIXED_AMOUNT) {
            discount = discountValue;
        } else {
            // PERCENTAGE
            discount = orderAmount.multiply(discountValue)
                    .divide(BigDecimal.valueOf(100), 0, java.math.RoundingMode.DOWN);
            if (maxDiscountAmount != null && discount.compareTo(maxDiscountAmount) > 0) {
                discount = maxDiscountAmount;
            }
        }
        // 할인 금액이 주문 금액을 초과할 수 없음
        return discount.min(orderAmount);
    }
}
