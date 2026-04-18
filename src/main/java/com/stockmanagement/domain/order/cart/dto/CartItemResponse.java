package com.stockmanagement.domain.order.cart.dto;

import com.stockmanagement.domain.order.cart.entity.CartItem;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/** 장바구니 아이템 응답 DTO. */
@Getter
@Builder
public class CartItemResponse {

    private Long productId;
    private String productName;
    private BigDecimal unitPrice;
    private int quantity;
    private BigDecimal subtotal;

    /** 상품 대표 이미지 URL — null이면 이미지 없음 */
    private String thumbnailUrl;
    /** 현재 가용 재고 수량 — null이면 재고 정보 미포함 */
    private Integer availableQuantity;
    /** 담은 수량만큼 구매 가능한지 여부 — null이면 재고 정보 미포함 */
    private Boolean isAvailable;

    public static CartItemResponse from(CartItem item) {
        return from(item, null);
    }

    public static CartItemResponse from(CartItem item, Integer availableQuantity) {
        BigDecimal unitPrice = item.getProduct().getPrice();
        return CartItemResponse.builder()
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .thumbnailUrl(item.getProduct().getThumbnailUrl())
                .unitPrice(unitPrice)
                .quantity(item.getQuantity())
                .subtotal(unitPrice.multiply(BigDecimal.valueOf(item.getQuantity())))
                .availableQuantity(availableQuantity)
                .isAvailable(availableQuantity != null ? availableQuantity >= item.getQuantity() : null)
                .build();
    }
}
