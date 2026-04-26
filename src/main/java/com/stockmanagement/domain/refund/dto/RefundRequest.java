package com.stockmanagement.domain.refund.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RefundRequest {

    @NotNull
    private Long paymentId;

    @NotBlank
    @Size(max = 300)
    private String reason;
}
