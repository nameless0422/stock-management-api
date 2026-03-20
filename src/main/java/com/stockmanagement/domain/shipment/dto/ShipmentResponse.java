package com.stockmanagement.domain.shipment.dto;

import com.stockmanagement.domain.shipment.entity.Shipment;
import com.stockmanagement.domain.shipment.entity.ShipmentStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** 배송 응답 DTO. */
@Getter
@Builder
public class ShipmentResponse {

    private Long id;
    private Long orderId;
    private ShipmentStatus status;
    private String carrier;
    private String trackingNumber;
    private LocalDateTime shippedAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime createdAt;

    public static ShipmentResponse from(Shipment shipment) {
        return ShipmentResponse.builder()
                .id(shipment.getId())
                .orderId(shipment.getOrderId())
                .status(shipment.getStatus())
                .carrier(shipment.getCarrier())
                .trackingNumber(shipment.getTrackingNumber())
                .shippedAt(shipment.getShippedAt())
                .deliveredAt(shipment.getDeliveredAt())
                .createdAt(shipment.getCreatedAt())
                .build();
    }
}
