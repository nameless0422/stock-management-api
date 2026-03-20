package com.stockmanagement.domain.order.cart.dto;

import com.stockmanagement.domain.order.cart.entity.CartItem;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/** 장바구니 전체 응답 DTO. */
@Getter
@Builder
public class CartResponse {

    private Long userId;
    private List<CartItemResponse> items;
    private BigDecimal totalAmount;

    public static CartResponse from(Long userId, List<CartItem> cartItems) {
        List<CartItemResponse> itemResponses = cartItems.stream()
                .map(CartItemResponse::from)
                .toList();
        BigDecimal total = itemResponses.stream()
                .map(CartItemResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return CartResponse.builder()
                .userId(userId)
                .items(itemResponses)
                .totalAmount(total)
                .build();
    }
}
