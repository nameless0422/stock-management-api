package com.stockmanagement.domain.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Response DTO for payment preparation.
 *
 * <p>Contains the values the client must pass to the TossPayments checkout widget:
 * <ul>
 *   <li>{@code tossOrderId}    → widget's {@code orderId} parameter
 *   <li>{@code amount}         → widget's {@code amount} parameter
 *   <li>{@code orderName}      → widget's {@code orderName} parameter
 *   <li>{@code customerName}   → widget's {@code customerName} parameter
 *   <li>{@code customerEmail}  → widget's {@code customerEmail} parameter
 * </ul>
 *
 * <p>Using the server-returned {@code amount} (instead of a client-computed value)
 * prevents clients from manipulating the payment amount.
 */
@Getter
@AllArgsConstructor
public class PaymentPrepareResponse {

    /** Unique orderId to pass to TossPayments checkout widget. */
    private final String tossOrderId;

    /** Server-verified payment amount – use this value for the widget, not a client-computed one. */
    private final BigDecimal amount;

    /** Human-readable order name shown in the checkout UI (e.g. "iPhone 15 외 2건"). */
    private final String orderName;

    /** 결제창 구매자 이름 (TossPayments customerName 파라미터). */
    private final String customerName;

    /** 결제창 구매자 이메일 (TossPayments customerEmail 파라미터). */
    private final String customerEmail;
}
