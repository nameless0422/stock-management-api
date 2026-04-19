package com.stockmanagement.domain.order.dto;

import com.stockmanagement.domain.order.entity.OrderItem;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;

/**
 * 주문 항목 응답 DTO.
 *
 * <p>{@link OrderItem} 엔티티를 클라이언트에게 노출할 형태로 변환한다.
 * {@code @Jacksonized}로 Redis 캐시 역직렬화를 지원한다.
 */
@Getter
@Builder
@Jacksonized
public class OrderItemResponse {

    private final Long id;
    private final Long productId;

    /** 상품명 — 주문 당시 이름 (Product 참조를 통해 현재 이름을 반환) */
    private final String productName;

    /** 주문 수량 */
    private final int quantity;

    /** 주문 당시 단가 */
    private final BigDecimal unitPrice;

    /** 소계 = unitPrice × quantity */
    private final BigDecimal subtotal;

    /** 리뷰 작성 여부 — null이면 정보 미포함, true/false면 작성 여부 */
    private final Boolean hasReview;

    /** 상품 대표 이미지 URL — null이면 이미지 없음 */
    private final String thumbnailUrl;

    /** OrderItem 엔티티를 응답 DTO로 변환 (hasReview 미포함). */
    public static OrderItemResponse from(OrderItem item) {
        return from(item, null);
    }

    /** OrderItem 엔티티를 hasReview 포함 응답 DTO로 변환. */
    public static OrderItemResponse from(OrderItem item, Boolean hasReview) {
        return OrderItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .subtotal(item.getSubtotal())
                .hasReview(hasReview)
                .thumbnailUrl(item.getProduct().getThumbnailUrl())
                .build();
    }
}
