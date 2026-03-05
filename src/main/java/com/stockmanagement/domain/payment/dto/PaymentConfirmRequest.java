package com.stockmanagement.domain.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO for payment confirmation.
 *
 * <p>The client forwards the values received from TossPayments after
 * successful checkout. The server uses these values to call the
 * TossPayments confirmation API and finalize the payment.
 */
@Getter
@NoArgsConstructor
public class PaymentConfirmRequest {

    /** TossPayments-assigned payment key received from the checkout widget. */
    @NotBlank(message = "paymentKey is required")
    private String paymentKey;

    /** Our tossOrderId previously returned by the prepare endpoint. */
    @NotBlank(message = "tossOrderId is required")
    private String tossOrderId;

    /**
     * Payment amount returned by the checkout widget.
     * The server re-validates this against the DB-stored amount to prevent tampering.
     */
    @NotNull(message = "amount is required")
    @DecimalMin(value = "1", message = "amount must be at least 1")
    private BigDecimal amount;
}
