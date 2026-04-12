package com.stockmanagement.domain.refund.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "refunds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false, unique = true, updatable = false)
    private Long paymentId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 300)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundStatus status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime completedAt;

    @Builder
    private Refund(Long paymentId, Long orderId, BigDecimal amount, String reason) {
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.amount = amount;
        this.reason = reason;
        this.status = RefundStatus.PENDING;
    }

    /** 환불 처리가 완료되었다. */
    public void complete() {
        this.status = RefundStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    /** 환불 처리가 실패했다. */
    public void fail() {
        this.status = RefundStatus.FAILED;
    }

    /**
     * 이전 실패 이력을 재활용하여 재시도 준비 상태로 초기화한다.
     * FAILED 상태에서만 호출해야 한다.
     */
    public void reset(String newReason) {
        this.reason = newReason;
        this.status = RefundStatus.PENDING;
    }
}
