package com.stockmanagement.domain.order.dto;

import com.stockmanagement.domain.payment.dto.PaymentResponse;
import com.stockmanagement.domain.refund.dto.RefundResponse;
import com.stockmanagement.domain.shipment.dto.ShipmentResponse;
import lombok.Builder;
import lombok.Getter;

/**
 * 주문 상세 통합 응답 DTO.
 *
 * <p>주문 + 결제 + 배송 정보를 단일 응답으로 묶어 프론트엔드의 다중 API 호출을 제거한다.
 * payment/shipment는 존재하지 않을 수 있으므로 nullable.
 */
@Getter
@Builder
public class OrderDetailResponse {

    private final OrderResponse order;

    /** 결제 정보 — 결제 시작 전이면 null */
    private final PaymentResponse payment;

    /** 배송 정보 — 결제 완료 전이면 null */
    private final ShipmentResponse shipment;

    /** 환불 정보 — 환불 요청 전이면 null */
    private final RefundResponse refund;
}
