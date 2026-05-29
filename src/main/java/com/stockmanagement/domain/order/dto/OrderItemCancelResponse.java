package com.stockmanagement.domain.order.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 주문 아이템 부분 취소 응답 DTO.
 */
@Getter
@Builder
public class OrderItemCancelResponse {

    /** 주문 ID */
    private final Long orderId;

    /** 변경된 주문 상태 */
    private final String orderStatus;

    /** Toss 환불 금액 */
    private final BigDecimal refundAmount;

    /** 반환된 포인트 */
    private final long refundedPoints;

    /** 취소된 아이템 목록 */
    private final List<OrderItemResponse> cancelledItems;
}
