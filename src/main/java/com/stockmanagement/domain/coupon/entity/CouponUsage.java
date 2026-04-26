package com.stockmanagement.domain.coupon.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 쿠폰 사용 이력. 주문당 쿠폰 1개 (order_id UNIQUE). */
@Entity
@Table(name = "coupon_usages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true)
    private Long orderId;

    /** 실제 할인된 금액 (쿠폰 계산 결과). */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal discountAmount;

    @Column(nullable = false)
    private LocalDateTime usedAt;

    @Builder
    public CouponUsage(Coupon coupon, Long userId, Long orderId, BigDecimal discountAmount) {
        this.coupon = coupon;
        this.userId = userId;
        this.orderId = orderId;
        this.discountAmount = discountAmount;
        this.usedAt = LocalDateTime.now();
    }
}
