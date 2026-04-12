package com.stockmanagement.domain.payment.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Request body for TossPayments payment cancellation API.
 * POST https://api.tosspayments.com/v1/payments/{paymentKey}/cancel
 */
@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TossCancelRequest {

    /** Mandatory human-readable reason for cancellation. */
    private final String cancelReason;

    /**
     * Amount to cancel.
     * Omit (null) for a full cancellation.
     * Provide a positive value for a partial refund.
     */
    private final BigDecimal cancelAmount;
}
