package com.stockmanagement.domain.payment.infrastructure.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Request body for TossPayments payment confirmation API.
 * POST https://api.tosspayments.com/v1/payments/confirm
 */
@Getter
@AllArgsConstructor
public class TossConfirmRequest {

    /** TossPayments-assigned payment key received from the checkout widget. */
    private final String paymentKey;

    /** The orderId we sent to TossPayments (our tossOrderId). */
    private final String orderId;

    /** Payment amount – must match the amount used when initializing the widget. */
    private final BigDecimal amount;
}
