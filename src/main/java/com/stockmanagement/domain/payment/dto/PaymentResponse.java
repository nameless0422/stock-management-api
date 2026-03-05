package com.stockmanagement.domain.payment.dto;

import com.stockmanagement.domain.payment.entity.Payment;
import com.stockmanagement.domain.payment.entity.PaymentStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO representing payment details.
 * Used for confirm, cancel, and query endpoints.
 */
@Getter
@Builder
public class PaymentResponse {

    private Long id;
    private Long orderId;
    private String paymentKey;
    private String tossOrderId;
    private BigDecimal amount;
    private PaymentStatus status;
    private String method;
    private LocalDateTime requestedAt;
    private LocalDateTime approvedAt;
    private String cancelReason;
    private String failureCode;
    private String failureMessage;
    private LocalDateTime createdAt;

    /** Converts a {@link Payment} entity to a response DTO. */
    public static PaymentResponse from(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrderId())
                .paymentKey(payment.getPaymentKey())
                .tossOrderId(payment.getTossOrderId())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .method(payment.getMethod())
                .requestedAt(payment.getRequestedAt())
                .approvedAt(payment.getApprovedAt())
                .cancelReason(payment.getCancelReason())
                .failureCode(payment.getFailureCode())
                .failureMessage(payment.getFailureMessage())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
