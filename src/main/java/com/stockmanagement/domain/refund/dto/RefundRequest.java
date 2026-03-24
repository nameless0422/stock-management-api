package com.stockmanagement.domain.refund.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RefundRequest {

    @NotNull
    private Long paymentId;

    @NotBlank
    private String reason;
}
