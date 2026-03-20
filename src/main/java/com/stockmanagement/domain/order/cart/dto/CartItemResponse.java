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

    public static CartItemResponse from(CartItem item) {
        BigDecimal unitPrice = item.getProduct().getPrice();
        return CartItemResponse.builder()
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .unitPrice(unitPrice)
                .quantity(item.getQuantity())
                .subtotal(unitPrice.multiply(BigDecimal.valueOf(item.getQuantity())))
                .build();
    }
}
