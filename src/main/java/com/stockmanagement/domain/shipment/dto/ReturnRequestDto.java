package com.stockmanagement.domain.shipment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ReturnRequestDto {

    @NotBlank
    @Size(max = 500)
    private String reason;
}
