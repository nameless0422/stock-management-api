package com.stockmanagement.domain.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO for payment preparation.
 *
 * <p>The client calls {@code POST /api/payments/prepare} before rendering
 * the TossPayments checkout widget. The server validates the order and
 * returns a {@link PaymentPrepareResponse} containing the values to pass
 * to the widget ({@code tossOrderId} and {@code amount}).
 */
@Getter
@NoArgsConstructor
public class PaymentPrepareRequest {

    @NotNull(message = "orderId is required")
    private Long orderId;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "1", message = "amount must be at least 1")
    private BigDecimal amount;
}
