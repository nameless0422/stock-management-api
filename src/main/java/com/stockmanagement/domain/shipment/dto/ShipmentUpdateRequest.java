package com.stockmanagement.domain.shipment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 배송 출고 요청 DTO (택배사 + 운송장 번호). */
@Getter
@NoArgsConstructor
public class ShipmentUpdateRequest {

    @NotBlank(message = "택배사명은 필수입니다.")
    private String carrier;

    @NotBlank(message = "운송장 번호는 필수입니다.")
    private String trackingNumber;
}
