package com.stockmanagement.domain.payment.entity;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
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
 * Payment entity representing a single TossPayments transaction attempt.
 *
 * <p>Lifecycle:
 * <pre>
 *   PENDING ──→ DONE ──→ CANCELLED
 *           └──→ FAILED
 * </pre>
 *
 * <p>Design notes:
 * <ul>
 *   <li>No public setters – all state transitions go through business methods only.
 *   <li>{@code tossOrderId} is the value we send to TossPayments as {@code orderId}; unique per attempt.
 *   <li>{@code paymentKey} is assigned by TossPayments after successful confirmation; null until then.
 *   <li>{@code orderId} stores the FK to our {@code orders} table without a JPA association
 *       to keep payment and order domains loosely coupled.
 * </ul>
 */
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK to orders table – stored as a plain Long to avoid tight coupling with the Order entity. */
    @Column(nullable = false)
    private Long orderId;

    /** TossPayments-assigned payment key; null until the payment is confirmed. */
    @Column(length = 200)
    private String paymentKey;

    /** The orderId we sent to TossPayments; unique per payment attempt. */
    @Column(nullable = false, length = 64, unique = true)
    private String tossOrderId;

    /** Payment amount agreed at prepare time; used for server-side amount verification. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    /** Payment method returned by TossPayments (e.g. "카드", "가상계좌", "계좌이체"). */
    @Column(length = 50)
    private String method;

    /** Timestamp when the payment was requested (from TossPayments response). */
    private LocalDateTime requestedAt;

    /** Timestamp when TossPayments approved the payment. */
    private LocalDateTime approvedAt;

    /** 누적 취소 금액. 전액 취소 시 amount와 동일, 부분 취소 시 취소된 부분 합계. */
    @Column(precision = 12, scale = 2)
    private BigDecimal cancelledAmount;

    /** Reason provided by the caller when cancelling the payment. */
    @Column(length = 200)
    private String cancelReason;

    /** Error code returned by TossPayments when it rejects the payment. */
    @Column(length = 50)
    private String failureCode;

    /** Error message returned by TossPayments when it rejects the payment. */
    @Column(length = 200)
    private String failureMessage;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Payment(Long orderId, String tossOrderId, BigDecimal amount) {
        this.orderId = orderId;
        this.tossOrderId = tossOrderId;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
    }

    // ===== Business methods =====

    /**
     * Marks this payment as approved (DONE).
     * Stores the paymentKey, method, and timestamps received from TossPayments.
     *
     * @throws BusinessException if the current status is not PENDING
     */
    public void approve(String paymentKey, String method,
                        LocalDateTime requestedAt, LocalDateTime approvedAt) {
        if (this.status != PaymentStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_PAYMENT_STATUS);
        }
        this.paymentKey = paymentKey;
        this.method = method;
        this.requestedAt = requestedAt;
        this.approvedAt = approvedAt;
        this.status = PaymentStatus.DONE;
    }

    /**
     * 결제를 취소한다. 전액 취소 또는 부분 취소를 지원한다.
     *
     * <p>cancelAmount가 null이면 전액 취소(CANCELLED),
     * 누적 취소 금액이 결제 금액 미만이면 부분 취소(PARTIAL_CANCELLED),
     * 누적 취소 금액이 결제 금액 이상이면 전액 취소(CANCELLED)로 전환한다.
     *
     * @param cancelReason 취소 사유
     * @param cancelAmount 부분 취소 금액 (null이면 전액 취소)
     * @throws BusinessException DONE 또는 PARTIAL_CANCELLED 상태가 아닌 경우
     */
    public void cancel(String cancelReason, BigDecimal cancelAmount) {
        if (this.status != PaymentStatus.DONE && this.status != PaymentStatus.PARTIAL_CANCELLED) {
            throw new BusinessException(ErrorCode.INVALID_PAYMENT_STATUS);
        }
        this.cancelReason = cancelReason;

        if (cancelAmount == null) {
            // 전액 취소
            this.cancelledAmount = this.amount;
            this.status = PaymentStatus.CANCELLED;
        } else {
            // 부분 취소: 누적
            BigDecimal accumulated = (this.cancelledAmount != null ? this.cancelledAmount : BigDecimal.ZERO)
                    .add(cancelAmount);
            this.cancelledAmount = accumulated;

            if (accumulated.compareTo(this.amount) >= 0) {
                this.status = PaymentStatus.CANCELLED;
            } else {
                this.status = PaymentStatus.PARTIAL_CANCELLED;
            }
        }
    }

    /**
     * Marks this payment as failed.
     * Stores the failure details returned by TossPayments.
     *
     * @param failureCode    TossPayments error code
     * @param failureMessage TossPayments error message
     */
    public void fail(String failureCode, String failureMessage) {
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.status = PaymentStatus.FAILED;
    }

    /**
     * Resets a FAILED payment to PENDING for retry.
     *
     * <p>FAILED 결제가 존재할 때 사용자가 "다시 결제하기"를 선택하면 기존 레코드를 재사용한다.
     * {@code tossOrderId}는 TossPayments 측 UNIQUE 값이므로 새 UUID 기반 값으로 교체한다.
     *
     * @param newTossOrderId 새 결제 시도용 tossOrderId
     * @throws BusinessException if the current status is not FAILED
     */
    public void resetForRetry(String newTossOrderId) {
        if (this.status != PaymentStatus.FAILED) {
            throw new BusinessException(ErrorCode.INVALID_PAYMENT_STATUS);
        }
        this.tossOrderId = newTossOrderId;
        this.failureCode = null;
        this.failureMessage = null;
        this.status = PaymentStatus.PENDING;
    }
}
