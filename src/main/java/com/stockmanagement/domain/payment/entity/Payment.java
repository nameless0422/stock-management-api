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
     * Marks this payment as cancelled.
     * Only a DONE payment can be cancelled (a failed payment cannot be refunded).
     *
     * @param cancelReason human-readable reason for cancellation
     * @throws BusinessException if the current status is not DONE
     */
    public void cancel(String cancelReason) {
        if (this.status != PaymentStatus.DONE) {
            throw new BusinessException(ErrorCode.INVALID_PAYMENT_STATUS);
        }
        this.cancelReason = cancelReason;
        this.status = PaymentStatus.CANCELLED;
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
