package com.stockmanagement.domain.point.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "point_transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    /** 변동 금액 (양수=적립/환불, 음수=사용/소멸) */
    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PointTransactionType type;

    @Column(nullable = false, length = 200)
    private String description;

    /** 연관 주문 ID — nullable */
    @Column(name = "order_id")
    private Long orderId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private PointTransaction(Long userId, long amount, PointTransactionType type,
                             String description, Long orderId) {
        this.userId = userId;
        this.amount = amount;
        this.type = type;
        this.description = description;
        this.orderId = orderId;
    }
}
