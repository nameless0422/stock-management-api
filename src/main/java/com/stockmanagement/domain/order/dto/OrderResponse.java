package com.stockmanagement.domain.order.dto;

import com.stockmanagement.domain.order.entity.Order;
import com.stockmanagement.domain.order.entity.OrderStatus;
import com.stockmanagement.domain.shipment.entity.ShipmentStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 주문 응답 DTO.
 *
 * <p>{@link Order} 엔티티와 하위 {@link com.stockmanagement.domain.order.entity.OrderItem} 목록을
 * 클라이언트에게 노출할 형태로 변환한다.
 * {@code @Jacksonized}로 Redis 캐시 역직렬화를 지원한다.
 */
@Getter
@Builder
@Jacksonized
public class OrderResponse {

    private final Long id;
    /** 사용자 친화적 주문 번호 (예: 20240301-0000042) */
    private final String orderNumber;
    private final Long userId;
    private final OrderStatus status;
    private final BigDecimal totalAmount;
    private final String idempotencyKey;

    /** 선택된 배송지 ID — null이면 배송지 미지정 */
    private final Long deliveryAddressId;

    /** 적용된 쿠폰 ID — null이면 쿠폰 미사용 */
    private final Long couponId;

    /** 쿠폰 할인 금액 */
    private final BigDecimal discountAmount;

    /** 사용한 포인트 */
    private final long usedPoints;

    /** 주문 항목 목록 */
    private final List<OrderItemResponse> items;

    /** 배송 상태 — null이면 배송 미생성 (결제 완료 전 또는 취소된 주문) */
    private final ShipmentStatus shipmentStatus;

    /** 취소 사유 — CANCELLED 상태가 아니거나 사유 미입력 시 null */
    private final String cancelReason;

    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    /** Order 엔티티를 응답 DTO로 변환하는 정적 팩토리 메서드 */
    public static OrderResponse from(Order order) {
        return from(order, null, null);
    }

    /**
     * Order 엔티티를 hasReview + shipmentStatus 포함 응답 DTO로 변환한다.
     *
     * @param reviewedProductIds 현재 사용자가 리뷰를 작성한 상품 ID 집합 (null이면 hasReview=null)
     * @param shipmentStatus     배송 상태 (null이면 배송 미생성)
     */
    public static OrderResponse from(Order order, java.util.Set<Long> reviewedProductIds,
                                     ShipmentStatus shipmentStatus) {
        String orderNumber = order.getCreatedAt() != null
                ? order.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                        + "-" + String.format("%07d", order.getId())
                : null;
        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(orderNumber)
                .userId(order.getUserId())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .idempotencyKey(order.getIdempotencyKey())
                .deliveryAddressId(order.getDeliveryAddressId())
                .couponId(order.getCouponId())
                .discountAmount(order.getDiscountAmount())
                .usedPoints(order.getUsedPoints())
                .items(order.getItems().stream()
                        .map(i -> OrderItemResponse.from(i,
                                reviewedProductIds != null
                                        ? reviewedProductIds.contains(i.getProduct().getId())
                                        : null))
                        .toList())
                .shipmentStatus(shipmentStatus)
                .cancelReason(order.getCancelReason())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
