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

    /** 트랜잭션 확정 상태 — EARN 타입의 적립 예정/확정/만료 관리 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PointTransactionStatus status;

    /** 적립 포인트 만료 예정일 — EARN 타입에만 설정 */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private PointTransaction(Long userId, long amount, PointTransactionType type,
                             String description, Long orderId, PointTransactionStatus status,
                             LocalDateTime expiresAt) {
        this.userId = userId;
        this.amount = amount;
        this.type = type;
        this.description = description;
        this.orderId = orderId;
        this.status = status != null ? status : PointTransactionStatus.CONFIRMED;
        this.expiresAt = expiresAt;
    }

    /** PENDING → CONFIRMED 전환 */
    public void confirm() {
        if (this.status != PointTransactionStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태만 확정 가능: current=" + this.status);
        }
        this.status = PointTransactionStatus.CONFIRMED;
    }

    /** PENDING → EXPIRED 전환 (주문 취소 시) */
    public void expire() {
        if (this.status != PointTransactionStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태만 만료 가능: current=" + this.status);
        }
        this.status = PointTransactionStatus.EXPIRED;
    }

    /** CONFIRMED → EXPIRED 전환 (기간 만료 시) */
    public void expireConfirmed() {
        if (this.status != PointTransactionStatus.CONFIRMED) {
            throw new IllegalStateException("CONFIRMED 상태만 기간 만료 가능: current=" + this.status);
        }
        this.status = PointTransactionStatus.EXPIRED;
    }
}
