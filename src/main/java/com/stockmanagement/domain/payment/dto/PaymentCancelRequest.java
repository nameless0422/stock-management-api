package com.stockmanagement.domain.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    @Size(max = 200, message = "cancelReason must not exceed 200 characters")
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

    /** 내부 호출용 팩토리 메서드 (전액 취소). */
    public static PaymentCancelRequest of(String cancelReason) {
        PaymentCancelRequest req = new PaymentCancelRequest();
        req.cancelReason = cancelReason;
        return req;
    }
}
