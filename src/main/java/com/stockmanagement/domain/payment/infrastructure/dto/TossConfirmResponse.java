package com.stockmanagement.domain.payment.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Response from TossPayments payment confirmation / query API.
 *
 * <p>Only the fields relevant to our domain are mapped.
 * Unknown fields from the TossPayments response are silently ignored.
 *
 * @see <a href="https://docs.tosspayments.com/reference">TossPayments API Reference</a>
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TossConfirmResponse {

    /** TossPayments-assigned unique payment key. */
    private String paymentKey;

    /** The orderId we sent to TossPayments (our tossOrderId). */
    private String orderId;

    /**
     * Payment result status.
     * Typical values: "DONE", "CANCELED", "WAITING_FOR_DEPOSIT", "ABORTED", "EXPIRED".
     */
    private String status;

    /** Total payment amount. */
    private BigDecimal totalAmount;

    /** Payment method (e.g. "카드", "가상계좌", "계좌이체", "휴대폰"). */
    private String method;

    /** ISO-8601 timestamp when the payment was requested. */
    private String requestedAt;

    /** ISO-8601 timestamp when TossPayments approved the payment. */
    private String approvedAt;

    /** Failure details populated when the payment is rejected or cancelled. */
    private Failure failure;

    /** Inner class representing TossPayments failure details. */
    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Failure {
        /** TossPayments error code (e.g. "CARD_DECLINED", "INVALID_CARD_EXPIRY_DATE"). */
        private String code;
        /** Human-readable error message in Korean. */
        private String message;
    }
}
