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
    /** 배송사 트래킹 URL — carrier + trackingNumber 조합으로 자동 생성. null이면 미지원 배송사 */
    private String trackingUrl;
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
                .trackingUrl(buildTrackingUrl(shipment.getCarrier(), shipment.getTrackingNumber()))
                .shippedAt(shipment.getShippedAt())
                .deliveredAt(shipment.getDeliveredAt())
                .createdAt(shipment.getCreatedAt())
                .build();
    }

    /** 국내 주요 배송사 트래킹 URL을 생성한다. 미지원 배송사는 null 반환. */
    private static String buildTrackingUrl(String carrier, String trackingNumber) {
        if (carrier == null || trackingNumber == null) return null;
        return switch (carrier.toUpperCase()) {
            case "CJ대한통운", "CJ" ->
                    "https://trace.cjlogistics.com/next/tracking.html?wblNo=" + trackingNumber;
            case "한진택배", "HANJIN" ->
                    "https://www.hanjin.com/kor/CMS/DeliveryMgr/WaybillResult.do?mCode=MN038&schLang=KR&wblnumText2=" + trackingNumber;
            case "롯데택배", "LOTTE" ->
                    "https://www.lotteglogis.com/home/reservation/tracking/linkView?InvNo=" + trackingNumber;
            case "우체국택배", "EPOST" ->
                    "https://service.epost.go.kr/trace.RetrieveRegiTraceList.comm?sid1=" + trackingNumber;
            default -> null;
        };
    }
}
