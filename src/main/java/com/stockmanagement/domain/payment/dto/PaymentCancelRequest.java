package com.stockmanagement.domain.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO for payment cancellation / refund.
 */
@Getter
@NoArgsConstructor
public class PaymentCancelRequest {

    /** Mandatory human-readable reason for the cancellation. */
    @NotBlank(message = "cancelReason is required")
    private String cancelReason;

    /**
     * Amount to cancel.
     * <ul>
     *   <li>Omit (null) for a full cancellation.
     *   <li>Provide a positive value for a partial refund.
     * </ul>
     */
    @DecimalMin(value = "1", message = "cancelAmount must be at least 1")
    private BigDecimal cancelAmount;
}
