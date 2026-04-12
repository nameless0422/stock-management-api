package com.stockmanagement.domain.shipment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 배송 출고 요청 DTO (택배사 + 운송장 번호). */
@Getter
@NoArgsConstructor
public class ShipmentUpdateRequest {

    @NotBlank(message = "택배사명은 필수입니다.")
    @Size(max = 100, message = "택배사명은 100자 이하여야 합니다.")
    private String carrier;

    @NotBlank(message = "운송장 번호는 필수입니다.")
    @Size(max = 50, message = "운송장 번호는 50자 이하여야 합니다.")
    @Pattern(regexp = "^[A-Za-z0-9\\-]+$", message = "운송장 번호는 영문, 숫자, 하이픈만 허용됩니다.")
    private String trackingNumber;
}
