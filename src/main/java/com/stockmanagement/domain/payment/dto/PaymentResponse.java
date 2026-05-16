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
    private BigDecimal cancelledAmount;
    private String cancelReason;
    private String failureCode;
    private String failureMessage;
    private LocalDateTime createdAt;

    /** 가상계좌 입금 정보 — 가상계좌 결제 시에만 non-null. */
    private VirtualAccount virtualAccount;

    /** 주문 요약 정보 (상품명, 건수, 썸네일) — getMyPayments 등에서 제공. */
    private OrderSummary orderSummary;

    /** 가상계좌 입금 정보. */
    public record VirtualAccount(String bank, String accountNumber, LocalDateTime dueDate) {}

    /** 결제에 연결된 주문의 요약 정보. */
    public record OrderSummary(String orderName, int itemCount, String thumbnailUrl) {}

    /** Converts a {@link Payment} entity to a response DTO. */
    public static PaymentResponse from(Payment payment) {
        return from(payment, null);
    }

    /** Converts a {@link Payment} entity to a response DTO with order summary. */
    public static PaymentResponse from(Payment payment, OrderSummary orderSummary) {
        VirtualAccount va = payment.getVirtualAccountBank() != null
                ? new VirtualAccount(payment.getVirtualAccountBank(),
                        payment.getVirtualAccountNumber(),
                        payment.getVirtualAccountDueDate())
                : null;
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
                .cancelledAmount(payment.getCancelledAmount())
                .cancelReason(payment.getCancelReason())
                .failureCode(payment.getFailureCode())
                .failureMessage(payment.getFailureMessage())
                .createdAt(payment.getCreatedAt())
                .virtualAccount(va)
                .orderSummary(orderSummary)
                .build();
    }
}
