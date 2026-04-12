package com.stockmanagement.domain.coupon.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 관리자가 특정 사용자에게 발급한 쿠폰. */
@Entity
@Table(name = "user_coupons",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "coupon_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    @Column(nullable = false)
    private LocalDateTime issuedAt;

    @Builder
    public UserCoupon(Long userId, Coupon coupon) {
        this.userId = userId;
        this.coupon = coupon;
        this.issuedAt = LocalDateTime.now();
    }
}
